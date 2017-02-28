package application;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.JAXB;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.p2.repository.AuthenticationFailedException;
import org.eclipse.equinox.internal.p2.repository.DownloadStatus;
import org.eclipse.equinox.internal.p2.repository.Transport;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;

import application.jaxb.Mirror;
import application.jaxb.Mirrors;

public class MyTransport extends Transport {
	final static int BUFFER_SIZE = 4096;

	@Override
	public IStatus download(URI toDownload, OutputStream target, long startPos, IProgressMonitor monitor) {
		// does not seem to be used by p2 at all
		throw new UnsupportedOperationException();
	}

	private IStatus mirror(URI uri, Path path, IProgressMonitor monitor) {
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

					// FIXME
					return Status.OK_STATUS;
				} catch (NoSuchFileException e) {

				}

				System.err.println(uri + " -> " + path);

				try (CloseableHttpResponse response = httpClient.execute(get)) {

					HttpEntity entity = response.getEntity();
					switch (response.getStatusLine().getStatusCode()) {
					case HttpStatus.SC_UNAUTHORIZED:
						result = new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
								ProvisionException.REPOSITORY_FAILED_AUTHENTICATION, "authentication failed " + uri,
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

						result = Status.OK_STATUS;
						break;
					case HttpStatus.SC_NOT_MODIFIED:
						result = Status.OK_STATUS;
						break;
					case HttpStatus.SC_NOT_FOUND:
						EntityUtils.consume(entity);
						result = new DownloadStatus(IStatus.ERROR, Activator.PLUGIN_ID,
								ProvisionException.ARTIFACT_NOT_FOUND, "not found: " + uri, null);
						break;
					default:
						throw new Error(get + " -> " + response);
					}

				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	@Override
	public IStatus download(URI toDownload, OutputStream target, IProgressMonitor monitor) {
		List<String> collect = mirrorFiles.values().stream().flatMap(p -> Arrays.stream(p.mirrors).map(p1 -> {
			if (toDownload.toString().startsWith(p1.url.toString()))
				return p.base + toDownload.toString().substring(p1.url.toString().length());
			else
				return null;
		}).filter(p1 -> p1 != null)).collect(Collectors.toList());

		Path p;
		switch (collect.size()) {
		case 0:
			p = toPath(toDownload);
			break;
		case 1:
			p = toPath(URI.create(collect.iterator().next()));
			break;
		default:
			throw new Error("collect=" + collect);
		}

		IStatus result = mirror(toDownload, p, monitor);

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
		Path root = Paths.get("c:/temp/mirror2");

		root = root.resolve(toDownload.getScheme());
		root = root.resolve(toDownload.getHost());

		String p = toDownload.getPath();
		if (!p.startsWith("/"))
			throw new Error();
		p = p.substring(1);

		String q = toDownload.getQuery();
		if (q != null)
			p += "_" + q;

		root = root.resolve(p);

		return root;
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
			System.err.println("Not a recogized mirror: " + toDownload);

		try {
			return Files.newInputStream(p);
		} catch (IOException e) {
			throw new Error("Could not open " + p + ": " + e, e);
		}
	}

	@Override
	public long getLastModified(URI toDownload, IProgressMonitor monitor)
			throws CoreException, FileNotFoundException, AuthenticationFailedException {

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
		public RepoMirror(String base, String mirrorFile) {
			this.base = base;
			this.mirrorFile = mirrorFile;
		}

		final String mirrorFile;
		final String base;

		Mirror[] mirrors = new Mirror[0];

		@Override
		public String toString() {
			return "RepoMirror(mirrorFile=" + mirrorFile + ",base=" + base + ")";
		}
	}

	private Map<URI, RepoMirror> mirrorFiles = new HashMap<>();

	public void addRepositories(Collection<IArtifactRepository> repositories) {
		for (IArtifactRepository repo : repositories) {
			String base = repo.getProperties().get(IRepository.PROP_MIRRORS_BASE_URL);
			String mirror = repo.getProperties().get(IRepository.PROP_MIRRORS_URL);
			if (mirror == null)
				continue;

			if (base == null)
				base = repo.getLocation().toString();

			mirrorFiles.put(repo.getLocation(), new RepoMirror(base, mirror));
		}
	}

}