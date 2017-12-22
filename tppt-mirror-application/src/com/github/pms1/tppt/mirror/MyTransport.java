package com.github.pms1.tppt.mirror;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.JAXB;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
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

import com.github.pms1.tppt.mirror.MirrorSpec.OfflineType;
import com.github.pms1.tppt.mirror.MirrorSpec.StatsType;
import com.github.pms1.tppt.mirror.jaxb.Mirror;
import com.github.pms1.tppt.mirror.jaxb.Mirrors;

@SuppressWarnings("restriction")
public class MyTransport extends Transport {
	private final static int BUFFER_SIZE = 4096;

	private final Path root;

	private final OfflineType offline;

	private final StatsType stats;

	// see p2's OfflineTransport
	private static final Status OFFLINE_STATUS = new Status(IStatus.ERROR, Activator.PLUGIN_ID, "offline");

	public MyTransport(Path root, OfflineType offline, StatsType stats) {
		Objects.requireNonNull(root);
		this.root = root;
		this.offline = offline;
		this.stats = stats;
	}

	@Override
	public IStatus download(URI toDownload, OutputStream target, long startPos, IProgressMonitor monitor) {
		// does not seem to be used by p2 at all
		throw new UnsupportedOperationException();
	}

	private static Map<Path, Integer> tries = new HashMap<>();

	Path last;

	private IStatus mirror(URI uri, Path path, IProgressMonitor monitor) {
		last = null;

		try {
			Files.createDirectories(path.getParent());
		} catch (IOException e) {
			throw new Error("Could not create directories: " + path.getParent() + ": " + e, e);
		}

		IStatus result;

		try {
			try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

				HttpGet get = new HttpGet(uri);

				try {
					FileTime ft = Files.getLastModifiedTime(path);

					get.addHeader(HttpHeaders.IF_MODIFIED_SINCE, DateUtils.formatDate(new Date(ft.toMillis())));

					int tryNr = tries.getOrDefault(path, 0) + 1;
					tries.put(path, tryNr);
					if (tryNr < 4) {
						// #9 should be able to specify somehow if validation is
						// to be done
						last = path;
						return Status.OK_STATUS;
					}

					System.out.println(path
							+ " is loaded repeatedly from cache. Assuming cached file is corrupted and re-mirroring it.");

					Files.delete(path);
				} catch (NoSuchFileException e) {

				}

				if (offline == OfflineType.offline)
					return OFFLINE_STATUS;

				System.out.println("Mirroring: " + uri + " -> " + path);

				try (CloseableHttpResponse response = httpClient.execute(get)) {

					HttpEntity entity = response.getEntity();
					switch (response.getStatusLine().getStatusCode()) {
					case HttpStatus.SC_UNAUTHORIZED:
						result = new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
								ProvisionException.REPOSITORY_FAILED_AUTHENTICATION, "Authentication failed " + uri,
								null);
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

								monitor.subTask("loading " + uri + " (" + convert(done) + "/" + totalc + " "
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

						System.err.println("R1 " + Files.exists(path));
						result = Status.OK_STATUS;
						break;
					case HttpStatus.SC_NOT_MODIFIED:
						System.err.println("R2 " + Files.exists(path));
						result = Status.OK_STATUS;
						break;
					case HttpStatus.SC_NOT_FOUND:
						System.err.println("R3 " + Files.exists(path));
						EntityUtils.consume(entity);
						result = new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
								ProvisionException.ARTIFACT_NOT_FOUND, "not found: " + uri, null);
						break;
					default:
						throw new Error(get + " -> " + response);
					}
				}
			}
		} catch (ConnectException e) {
			return new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID, ProvisionException.REPOSITORY_FAILED_READ,
					"foo", e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	@Override
	public IStatus download(URI toDownload, OutputStream target, IProgressMonitor monitor) {
		Set<String> collect = mirrorFiles.values().stream().flatMap(p -> Arrays.stream(p.mirrors).map(p1 -> {
			if (toDownload.toString().startsWith(p1.url.toString()))
				return p.base + toDownload.toString().substring(p1.url.toString().length());
			else
				return null;
		}).filter(p1 -> p1 != null)).collect(Collectors.toSet());

		Path p;
		switch (collect.size()) {
		case 0:
			p = toPath(toDownload);
			break;
		case 1:
			p = toPath(URI.create(collect.iterator().next()));
			break;
		default:
			throw new Error("URI '" + toDownload + "' was de-mirrored to different URIs: " + collect);
		}

		IStatus result = mirror(toDownload, p, monitor);
		System.err.println("R4 " + result + " " + p + " " + Files.exists(p));

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

	@Override
	public InputStream stream(URI toDownload, IProgressMonitor monitor)
			throws FileNotFoundException, CoreException, AuthenticationFailedException {

		Path p = toPath(toDownload);

		mirror(toDownload, p, monitor);

		boolean found = false;
		for (RepoMirror r : mirrorFiles.values()) {
			// p2 creates this by string concatenation so we don't to test for a
			// real prefix
			if (toDownload.toString().startsWith(r.mirrorFile)) {
				found = true;

				r.mirrors = JAXB.unmarshal(p.toFile(), Mirrors.class).mirror;
			}
		}

		if (!found)
			throw new Error("Not a recogized mirror: " + toDownload + " [mirrorFiles=" + mirrorFiles.values() + "]");

		try {
			return Files.newInputStream(p);
		} catch (IOException e) {
			throw new Error("Could not open " + p + ": " + e, e);
		}
	}

	@Override
	public long getLastModified(URI toDownload, IProgressMonitor monitor)
			throws CoreException, FileNotFoundException, AuthenticationFailedException {

		boolean isStats = mirrorFiles.values().stream()
				.anyMatch(p -> p.stats != null && toDownload.toString().startsWith(p.stats));

		if (isStats) {
			// FileNotFound is the expected result for statistics anyway, so we
			// can use it safely if we don't access them at all.

			if (offline == OfflineType.offline)
				throw new FileNotFoundException("Offline: " + toDownload);

			if (stats == StatsType.suppress)
				throw new FileNotFoundException("Accessing statistics was suppressed: " + toDownload);

			try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

				HttpHead get = new HttpHead(toDownload);

				try (CloseableHttpResponse response = httpClient.execute(get)) {
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
			} catch (IOException e) {
				throw new Error(e);
			}
		}

		Path p = toPath(toDownload);
		IStatus status = mirror(toDownload, p, monitor);

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
		public RepoMirror(String base, String mirrorFile, String stats) {
			this.base = base;
			this.mirrorFile = mirrorFile;
			this.stats = stats;
		}

		final String mirrorFile;
		final String base;
		final String stats;

		Mirror[] mirrors = new Mirror[0];

		@Override
		public String toString() {
			return "RepoMirror(mirrorFile=" + mirrorFile + ",base=" + base + ",stats=" + stats + ")";
		}
	}

	private Map<URI, RepoMirror> mirrorFiles = new HashMap<>();

	public void addRepositories(Collection<IArtifactRepository> repositories) {
		LinkedList<IArtifactRepository> todo = new LinkedList<>(repositories);
		while (!todo.isEmpty()) {
			IArtifactRepository repo = todo.removeFirst();
			String base = repo.getProperties().get(IRepository.PROP_MIRRORS_BASE_URL);
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
			if (base != null && base.isEmpty())
				base = null;
			if (stats != null && stats.isEmpty())
				stats = null;

			if (base == null)
				base = repo.getLocation().toString();

			mirrorFiles.put(repo.getLocation(), new RepoMirror(base, mirror, stats));
		}
	}

}
