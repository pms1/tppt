package com.github.pms1.tppt.mirror;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.bind.JAXB;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.repository.AuthenticationFailedException;
import org.eclipse.equinox.internal.p2.repository.DownloadStatus;
import org.eclipse.equinox.internal.p2.repository.Transport;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;

import com.github.pms1.tppt.mirror.MirrorSpec.AuthenticatedUri;
import com.github.pms1.tppt.mirror.MirrorSpec.OfflineType;
import com.github.pms1.tppt.mirror.MirrorSpec.StatsType;
import com.github.pms1.tppt.mirror.jaxb.Mirror;
import com.github.pms1.tppt.mirror.jaxb.Mirrors;
import com.github.pms1.tppt.mirror.jaxb.Proxy;

@SuppressWarnings("restriction")
public class MyTransport extends Transport {
	private final static int BUFFER_SIZE = 4096;

	private final Path root;

	private final OfflineType offline;

	private final StatsType stats;

	private final TreeMap<URI, AuthenticatedUri> servers = new TreeMap<>();

	private final TreeMap<URI, AuthenticatedUri> mavenMirrors = new TreeMap<>();

	private final TreeMap<URI, ServerParameters> serverParameters;

	static class ServerParameters {

		final UpdatePolicy updatePolicy;

		ServerParameters(UpdatePolicy updatePolicy) {
			this.updatePolicy = updatePolicy;
		}
	}

	// see p2's OfflineTransport
	private static final Status OFFLINE_STATUS = new Status(IStatus.ERROR, Activator.PLUGIN_ID, "offline");

	private final Proxy proxy;

	private final Pattern nonProxyHosts;

	private final CloseableHttpClient client, proxyClient;

	public MyTransport(Path root, OfflineType offline, StatsType stats, AuthenticatedUri[] servers,
			Map<URI, AuthenticatedUri> mavenMirrors, Proxy proxy, TreeMap<URI, ServerParameters> serverParameters) {
		Objects.requireNonNull(root);
		this.root = root;
		this.offline = offline;
		this.stats = stats;
		this.proxy = proxy;
		this.serverParameters = serverParameters;

		if (servers != null)
			for (AuthenticatedUri server : servers)
				this.servers.put(server.uri, server);
		if (mavenMirrors != null)
			this.mavenMirrors.putAll(mavenMirrors);

		Pattern nonProxyHosts = null;

		if (proxy != null) {
			if (proxy.host == null || proxy.host.isEmpty())
				throw new IllegalArgumentException();
			if (proxy.protocol == null || proxy.protocol.isEmpty())
				throw new IllegalArgumentException();
			if (proxy.port == null || proxy.port < 0 || proxy.port > 65535)
				throw new IllegalArgumentException();

			if ((proxy.username != null) != (proxy.password != null))
				throw new IllegalArgumentException();

			if (proxy.nonProxyHosts != null && !proxy.nonProxyHosts.isEmpty()) {
				nonProxyHosts = Pattern.compile(
						proxy.nonProxyHosts.stream().map(MyTransport::toRegexp).collect(Collectors.joining("|")));
			}
		}

		this.nonProxyHosts = nonProxyHosts;

		client = proxy == null || nonProxyHosts != null ? buildClient(null) : null;
		proxyClient = proxy != null ? buildClient(proxy) : null;
	}

	private static String toRegexp(String s) {
		StringBuilder sb = new StringBuilder();

		for (char c : s.toCharArray()) {
			if (c == '*')
				sb.append(".*");
			else
				sb.append(Pattern.quote(String.valueOf(c)));
		}

		return sb.toString();
	}

	/**
	 * Not used by p2.
	 */
	@Override
	public IStatus download(URI toDownload, OutputStream target, long startPos, IProgressMonitor monitor) {
		throw new UnsupportedOperationException();
	}

	private Path last;

	private static final ServerParameters defaultServerParameters = new ServerParameters(UpdatePolicy.ALWAYS);

	private CloseableHttpClient buildClient(Proxy proxy) {
		HttpClientBuilder builder = HttpClientBuilder.create();

		builder = builder.setDefaultCredentialsProvider(new CredentialsProvider() {

			@Override
			public void setCredentials(AuthScope arg0, Credentials arg1) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Credentials getCredentials(AuthScope authScope) {
				return MyTransport.this.getCredentials(authScope);
			}

			@Override
			public void clear() {
				throw new UnsupportedOperationException();
			}
		});

		if (proxy != null)
			builder = builder.setProxy(new HttpHost(proxy.host, proxy.port));

		return builder.build();
	}

	private IStatus mirror(URI originalUri, AuthenticatedUri uri, Path path, IProgressMonitor monitor) {
		last = null;

		try {
			Files.createDirectories(path.getParent());
		} catch (IOException e) {
			throw new Error("Could not create directories: " + path.getParent() + ": " + e, e);
		}

		IStatus result;

		ServerParameters serverParameters;
		{
			Entry<URI, ServerParameters> findLongestPrefix = Uris.findLongestPrefix(this.serverParameters, originalUri);
			if (findLongestPrefix == null)
				serverParameters = defaultServerParameters;
			else
				serverParameters = findLongestPrefix.getValue();
		}

		try {
			boolean useProxy = useProxy(uri.uri);

			HttpGet get = new HttpGet(uri.uri);

			try {
				FileTime ft = Files.getLastModifiedTime(path);

				get.addHeader(HttpHeaders.IF_MODIFIED_SINCE, DateUtils.formatDate(new Date(ft.toMillis())));

				// #9 should be able to specify somehow if validation is
				// to be done

				last = path;
				return Status.OK_STATUS;
			} catch (NoSuchFileException e) {
			}

			if (offline == OfflineType.offline)
				return OFFLINE_STATUS;

			System.out.print("Mirroring: " + originalUri);
			if (!Objects.equals(originalUri, uri.uri))
				System.out.print(" from " + uri.uri);
			if (useProxy)
				System.out.print(" proxy " + proxy.host + ":" + proxy.port);
			System.out.println(" -> " + path);

			try (CloseableHttpResponse response = execute(uri, get)) {

				HttpEntity entity = response.getEntity();
				switch (response.getStatusLine().getStatusCode()) {
				case HttpStatus.SC_UNAUTHORIZED:
					result = new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
							ProvisionException.REPOSITORY_FAILED_AUTHENTICATION, "Authentication failed " + uri, null);
					break;
				case HttpStatus.SC_OK:
					try (OutputStream out = Files.newOutputStream(path)) {

						InputStream is = entity.getContent();

						long total = entity.getContentLength();
						String totalc = convert(total);
						monitor.beginTask(null,
								(total < 0 || total > Integer.MAX_VALUE) ? IProgressMonitor.UNKNOWN : (int) total);

						byte[] buf = new byte[BUFFER_SIZE];
						long done = 0;
						long start = System.currentTimeMillis();
						for (;;) {
							int r = is.read(buf);
							if (r == -1)
								break;
							out.write(buf, 0, r);
							done += r;

							// monitor handling as seen in
							// org.eclipse.equinox.internal.p2.transport.ecf.FileReader
							monitor.worked(r);

							long now = System.currentTimeMillis();

							int ms = (int) (now - start);

							int rate;

							if (ms == 0)
								rate = 0;
							else
								rate = (int) (1000L * total / ms);

							monitor.subTask("loading " + uri.uri + " (" + convert(done) + "/" + totalc + " "
									+ convert(rate) + "/s)");

							if (monitor.isCanceled()) {
								get.abort();
								throw new OperationCanceledException();
							}
						}

						monitor.done();
					}

					Date date = parseLastModified(response);
					if (date != null)
						Files.setLastModifiedTime(path, FileTime.fromMillis(date.getTime()));

					last = path;
					result = Status.OK_STATUS;
					break;
				case HttpStatus.SC_NOT_MODIFIED:
					last = path;
					result = Status.OK_STATUS;
					break;
				case HttpStatus.SC_NOT_FOUND:
					EntityUtils.consume(entity);
					result = new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
							ProvisionException.ARTIFACT_NOT_FOUND, "not found: " + uri.uri,
							new FileNotFoundException());
					break;

				case HttpStatus.SC_BAD_GATEWAY:
					EntityUtils.consume(entity);
					result = new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
							ProvisionException.REPOSITORY_FAILED_READ,
							"failed read: " + uri.uri + " -> " + response.getStatusLine(), null);
					break;
				default:
					throw new Error(get + " -> " + response);
				}
			}
		} catch (UnknownHostException e) {
			return new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
					ProvisionException.REPOSITORY_INVALID_LOCATION, "Unknown host: " + uri.uri + " " + e, e);
		} catch (ConnectException e) {
			return new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID, ProvisionException.REPOSITORY_FAILED_READ,
					"Connect failed: " + uri.uri + " " + e, e);
		} catch (IOException e) {
			throw new Error(e);
		}

		return result;
	}

	private boolean useProxy(URI uri) {
		return proxy != null && !nonProxyHosts.matcher(uri.getHost()).matches();
	}

	private Entry<URI, AuthenticatedUri> findMavenMirror(URI prefix) {
		return Uris.findLongestPrefix(mavenMirrors, prefix);
	}

	private Entry<URI, AuthenticatedUri> findServer(URI prefix) {
		return Uris.findLongestPrefix(servers, prefix);
	}

	private AuthenticatedUri applyLocalMirror(URI uri) {
		AuthenticatedUri r = new AuthenticatedUri();

		Entry<URI, AuthenticatedUri> e = findMavenMirror(uri);
		if (e != null) {
			r.uri = Uris.reparent(uri, e.getKey(), e.getValue().uri);
			r.username = e.getValue().username;
			r.password = e.getValue().password;
			return r;
		}

		r.uri = uri;
		e = findServer(uri);
		if (e != null) {
			r.username = e.getValue().username;
			r.password = e.getValue().password;
		}

		return r;
	}

	private URI p2unmirror(URI toDownload) {
		Set<URI> collect = p2mirrorFiles.values().stream() //
				.flatMap(p -> Arrays.stream(p.mirrors).map(p1 -> {
					if (Uris.isChild(p1.url, toDownload))
						return Uris.reparent(toDownload, p1.url, p.base);
					else
						return null;
				}).filter(Objects::nonNull)) //
				.collect(Collectors.toSet());

		switch (collect.size()) {
		case 0:
			return toDownload;
		case 1:
			return collect.iterator().next();
		default:
			throw new Error("URI '" + toDownload + "' was un-mirrored to different URIs: " + collect);
		}
	}

	/**
	 * Called by p2 for artifacts and possibly passes an URI from a p2 mirror as
	 * {@code toDownload}.
	 */
	@Override
	public IStatus download(URI toDownload, OutputStream target, IProgressMonitor monitor) {

		URI norm = p2unmirror(toDownload);
		Path p = toPath(norm);

		AuthenticatedUri auri;

		if (findMavenMirror(norm) != null)
			auri = applyLocalMirror(norm);
		else
			auri = applyLocalMirror(toDownload);

		IStatus result = mirror(toDownload, auri, p, monitor);

		if (result.isOK()) {
			try (InputStream is = Files.newInputStream(p)) {
				byte[] buf = new byte[BUFFER_SIZE];
				for (;;) {
					int r = is.read(buf);
					if (r == -1)
						break;
					target.write(buf, 0, r);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return result;
	}

	private Path toPath(URI toDownload) {
		Path path = root;

		path = path.resolve(toDownload.getScheme());
		path = path.resolve(toDownload.getHost());

		String p = toDownload.getPath();
		if (!p.startsWith("/"))
			throw new Error();
		p = p.substring(1);

		String q = toDownload.getQuery();
		if (q != null)
			p += "_" + q;

		path = path.resolve(p);

		return path;
	}

	/**
	 * Not used for artifacts, but only for mirror selection
	 */
	@Override
	public InputStream stream(URI toDownload, IProgressMonitor monitor)
			throws FileNotFoundException, CoreException, AuthenticationFailedException {

		Path p = toPath(toDownload);

		IStatus status = mirror(toDownload, applyLocalMirror(toDownload), p, monitor);
		if (!status.isOK()) {
			switch (status.getCode()) {
			case ProvisionException.ARTIFACT_NOT_FOUND:
				throw new FileNotFoundException(status.getMessage());
			case ProvisionException.REPOSITORY_FAILED_AUTHENTICATION:
				throw new AuthenticationFailedException();
			default:
				throw new CoreException(status);
			}
		}

		boolean found = false;
		for (RepoMirror r : p2mirrorFiles.values()) {
			// p2 creates this by string concatenation so we don't to test for a
			// real prefix
			if (toDownload.toString().startsWith(r.mirrorFile)) {
				found = true;

				r.mirrors = JAXB.unmarshal(p.toFile(), Mirrors.class).mirror;
				for (Mirror m : r.mirrors)
					m.url = Uris.normalizeDirectory(m.url);
			}
		}

		if (!found)
			throw new Error("Not a recogized mirror: " + toDownload + " [mirrorFiles=" + p2mirrorFiles.values() + "]");

		try {
			return Files.newInputStream(p);
		} catch (IOException e) {
			throw new Error("Could not open " + p + ": " + e, e);
		}
	}

	static boolean match(URI uri, AuthScope authScope) {
		if (!authScope.getScheme().equalsIgnoreCase("BASIC"))
			throw new IllegalArgumentException();
		if (authScope.getHost().equals(AuthScope.ANY_HOST))
			throw new IllegalArgumentException();
		if (authScope.getPort() == AuthScope.ANY_PORT)
			throw new IllegalArgumentException();

		if (!Objects.equals(uri.getHost(), authScope.getHost()))
			return false;

		int port = uri.getPort();
		if (port == -1) {
			switch (uri.getScheme()) {
			case "http":
				port = 80;
				break;
			case "https":
				port = 443;
				break;
			default:
				throw new IllegalArgumentException();
			}
		}
		if (port != authScope.getPort())
			return false;

		return true;
	}

	private AuthenticatedUri currentUri;

	private Proxy currentProxy;

	private Credentials getCredentials(AuthScope authScope) {
		if (currentProxy != null) {
			if (authScope.getHost().equals(currentProxy.host) && authScope.getPort() == currentProxy.port) {
				if (currentProxy.username == null || currentProxy.password == null)
					return null;
				else
					return new UsernamePasswordCredentials(currentProxy.username, currentProxy.password);
			}
		}

		if (currentUri == null || currentUri.username == null || currentUri.password == null)
			return null;

		if (match(currentUri.uri, authScope)) {
			return new UsernamePasswordCredentials(currentUri.username, currentUri.password);
		}

		return null;
	}

	CloseableHttpResponse execute(AuthenticatedUri uri, HttpUriRequest request)
			throws ClientProtocolException, IOException {

		currentUri = uri;
		try {
			CloseableHttpClient requestClient;
			if (useProxy(uri.uri)) {
				currentProxy = proxy;
				requestClient = proxyClient;
			} else {
				currentProxy = null;
				requestClient = client;
			}
			return requestClient.execute(request);
		} finally {
			currentUri = null;
			currentProxy = null;
		}
	}

	@Override
	public long getLastModified(URI toDownload, IProgressMonitor monitor)
			throws CoreException, FileNotFoundException, AuthenticationFailedException {

		boolean isStats = p2mirrorFiles.values().stream()
				.anyMatch(p -> p.stats != null && toDownload.toString().startsWith(p.stats));

		if (isStats) {
			// FileNotFound is the expected result for statistics anyway, so we
			// can use it safely if we don't access them at all.

			if (offline == OfflineType.offline)
				throw new FileNotFoundException("Offline: " + toDownload);

			if (stats == StatsType.suppress)
				throw new FileNotFoundException("Accessing statistics was suppressed: " + toDownload);

			AuthenticatedUri uri = applyLocalMirror(toDownload);

			try {

				HttpHead get = new HttpHead(uri.uri);

				try (CloseableHttpResponse response = execute(uri, get)) {
					switch (response.getStatusLine().getStatusCode()) {
					case HttpStatus.SC_UNAUTHORIZED:
						throw new AuthenticationFailedException();
					case HttpStatus.SC_OK:
						return parseLastModified(response).getTime();
					case HttpStatus.SC_NOT_FOUND:
						throw new FileNotFoundException("not found: " + toDownload);
					default:
						throw new Error(get + " -> " + response);
					}

				}
			} catch (FileNotFoundException | AuthenticationFailedException e) {
				throw e;

			} catch (UnknownHostException e) {
				throw new CoreException(new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
						ProvisionException.REPOSITORY_INVALID_LOCATION, "Unknown host: " + toDownload + " " + e, e));
			} catch (ConnectException e) {
				throw new CoreException(new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
						ProvisionException.REPOSITORY_FAILED_READ, "Connect failed: " + toDownload + " " + e, e));
			} catch (IOException e) {
				throw new Error(e);
			}
		}

		Path p = toPath(toDownload);
		IStatus status = mirror(toDownload, applyLocalMirror(toDownload), p, monitor);

		if (status.isOK()) {
			try {
				return Files.getLastModifiedTime(p).toMillis();
			} catch (IOException e) {
				throw new Error(e);
			}
		} else {
			switch (status.getCode()) {
			case ProvisionException.REPOSITORY_FAILED_AUTHENTICATION:
				throw new AuthenticationFailedException();
			case ProvisionException.ARTIFACT_NOT_FOUND:
				throw new FileNotFoundException("not found: " + toDownload);
			default:
				throw new Error("Unhandled status " + status);
			}

			// AuthenticationFailedException
			// } catch (UnknownHostException e) {
			// throw new CoreException(new DownloadStatus(IStatus.ERROR,
			// Activator.PLUGIN_ID,
			// ProvisionException.REPOSITORY_INVALID_LOCATION, "Unknown host " +
			// toDownload, e));
		}
	}

	private String convert(long amount) {
		if (amount < 1024) {
			return amount + "B";
		}
		amount /= 1024;
		if (amount < 1024) {
			return amount + "KB";
		}
		amount /= 1024;
		return amount + "MB";
	}

	private Date parseLastModified(HttpResponse response) {
		Header header = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);
		if (header == null) {
			return null;
		}
		String lm = header.getValue();
		if (lm == null) {
			return null;
		}

		return DateUtils.parseDate(lm);
	}

	static class RepoMirror {
		public RepoMirror(URI base, String mirrorFile, String stats) {
			this.base = base;
			this.mirrorFile = mirrorFile;
			this.stats = stats;
		}

		final String mirrorFile;
		final URI base;
		final String stats;

		Mirror[] mirrors = new Mirror[0];

		@Override
		public String toString() {
			return "RepoMirror(mirrorFile=" + mirrorFile + ",base=" + base + ",stats=" + stats + ")";
		}
	}

	private Map<URI, RepoMirror> p2mirrorFiles = new HashMap<>();

	public void addRepositories(Collection<IArtifactRepository> repositories) {
		LinkedList<IArtifactRepository> todo = new LinkedList<>(repositories);
		while (!todo.isEmpty()) {
			IArtifactRepository repo = todo.removeFirst();
			String baseString = repo.getProperties().get(IRepository.PROP_MIRRORS_BASE_URL);
			URI base = baseString != null && !baseString.isEmpty() ? URI.create(baseString) : null;
			String mirror = repo.getProperties().get(IRepository.PROP_MIRRORS_URL);
			String stats = repo.getProperties().get("p2.statsURI"); // no public
																	// constant

			CompositeArtifactRepository composite = repo.getAdapter(CompositeArtifactRepository.class);
			if (composite != null)
				todo.addAll(composite.getLoadedChildren());

			// constant
			if (mirror == null)
				continue;

			// be a bit paranoid and sanitize these values
			if (stats != null && stats.isEmpty())
				stats = null;

			if (base == null)
				base = repo.getLocation();

			p2mirrorFiles.put(repo.getLocation(), new RepoMirror(Uris.normalizeDirectory(base), mirror, stats));
		}
	}

	Path getLast() {
		return last;
	}
}
