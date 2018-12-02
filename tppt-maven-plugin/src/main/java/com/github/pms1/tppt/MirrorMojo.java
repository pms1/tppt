package com.github.pms1.tppt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.bind.JAXB;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Server;

import com.github.pms1.tppt.mirror.MirrorSpec;
import com.github.pms1.tppt.mirror.MirrorSpec.AlgorithmType;
import com.github.pms1.tppt.mirror.MirrorSpec.OfflineType;
import com.github.pms1.tppt.mirror.MirrorSpec.StatsType;
import com.github.pms1.tppt.mirror.jaxb.Proxy;
import com.github.pms1.tppt.p2.P2RepositoryFactory;

/**
 * A maven mojo for creating a p2 repository from maven dependencies
 * 
 * @author pms1
 **/
@Mojo(name = "mirror", requiresDependencyResolution = ResolutionScope.COMPILE)
public class MirrorMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
	private File target;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${mojoExecution}", readonly = true)
	private MojoExecution mojoExecution;

	@Component
	private RepositorySystem repositorySystem;

	@Parameter(readonly = true, required = true, defaultValue = "${project.remoteArtifactRepositories}")
	private List<ArtifactRepository> remoteRepositories;

	@Parameter(readonly = true, required = true, defaultValue = "${localRepository}")
	private ArtifactRepository localRepository;

	@Component
	private EquinoxRunnerFactory runnerFactory;

	@Component
	private TychoArtifactUnpacker installer;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

	@Component
	private P2RepositoryFactory p2repositoryFactory;

	@Component
	private ResolutionErrorHandler resolutionErrorHandler;

	@Parameter
	private List<Mirror> mirrors = Collections.emptyList();

	@Parameter
	private StatsType stats = StatsType.collect;

	public static class Mirror {
		@Parameter
		public List<String> ius;

		@Parameter
		public List<String> excludeIus;

		/**
		 * @deprecated
		 */
		@Parameter
		public List<URI> sources = Collections.emptyList();

		@SuppressWarnings("unchecked")
		@Parameter
		public Map<String, String>[] filters = new Map[0];

		@Parameter
		public AlgorithmType algorithm = AlgorithmType.permissiveSlicer;

		@Parameter(required = true)
		public List<Repository> source = Collections.emptyList();
	}

	public static class Repository {
		@Parameter
		public String id;

		@Parameter(required = true)
		public URI url;

		@Override
		public String toString() {
			return "Repository(id=" + id + ",url=" + url + ")";
		}
	}

	private static final String cacheRelPath = ".cache/tppt/p2";

	private URI findMirror(Repository r) {
		for (org.apache.maven.settings.Mirror m : session.getSettings().getMirrors()) {
			getLog().debug("Trying " + m + " for " + r);
			String result = matchMirror(m, r);
			if (result != null && !result.isEmpty()) {
				if (!result.endsWith("/"))
					result += "/";

				try {
					return new URI(m.getUrl());
				} catch (URISyntaxException e) {
					getLog().debug("Ignored " + m + " due to not an URI: " + e);
					continue;
				}
			}
		}

		return null;
	}

	private Map<URI, URI> findMirrors() {
		Map<URI, URI> x = new LinkedHashMap<>();
		for (org.apache.maven.settings.Mirror m : session.getSettings().getMirrors()) {
			if (!Objects.equals(m.getLayout(), "p2")) {
				getLog().debug("Ignored " + m + " due to wrong layout");
				continue;
			}
			if (!Objects.equals(m.getMirrorOfLayouts(), "p2")) {
				getLog().debug("Ignored " + m + " due to wrong mirrorOfLayout");
				continue;
			}

			URI from;
			try {
				from = new URI(m.getMirrorOf());
			} catch (URISyntaxException e) {
				getLog().debug("Ignored " + m + " due to not an URI: " + e);
				continue;
			}

			if (!from.isAbsolute()) {
				getLog().debug("Ignored " + m + " due to not an absolute URI: " + from);
				continue;
			}

			URI to;
			try {
				to = new URI(m.getUrl());
			} catch (URISyntaxException e) {
				getLog().debug("Ignored " + m + " due to not an URI: " + e);
				continue;
			}

			x.put(from, to);
		}
		return x;
	}

	private String matchMirror(org.apache.maven.settings.Mirror m, Repository r) {
		if (!Objects.equals(m.getLayout(), "p2")) {
			getLog().debug("Ignored " + m + " due to wrong layout");
			return null;
		}
		if (!Objects.equals(m.getMirrorOfLayouts(), "p2")) {
			getLog().debug("Ignored " + m + " due to wrong mirrorOfLayout");
			return null;
		}

		if (Objects.equals(m.getMirrorOf(), r.id)) {
			getLog().debug("Matched " + m + " on id");
			return m.getUrl();
		}

		getLog().debug("No match " + m + "");
		return null;
	}

	String toString(Repository r) {
		if (r.id != null && !r.id.isEmpty())
			return "'" + r.id + "'";
		else
			return "at '" + r.url + "'";
	}

	void findServers() {
		for (Server s : session.getSettings().getServers()) {

			System.err.println("SERVER " + s.getId() + " " + s.getConfiguration()
					+ (s.getConfiguration() != null ? s.getConfiguration().getClass() : "<none>"));
		}
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			final Path repoOut = target.toPath().resolve("repository");

			findServers();

			Map<URI, URI> uriMirrors = findMirrors();

			for (Mirror m : mirrors) {
				MirrorSpec ms = new MirrorSpec();

				ms.ius = m.ius.toArray(new String[m.ius.size()]);
				if (m.excludeIus != null)
					ms.excludeIus = m.excludeIus.toArray(new String[m.excludeIus.size()]);
				ms.mirrorRepository = Paths.get(session.getLocalRepository().getBasedir()).resolve(cacheRelPath);

				List<URI> repos = new ArrayList<>();
				if (!m.sources.isEmpty()) {
					getLog().warn(
							"Obsolete parameter 'sources' used. Use 'source' instead. 'sources' will be removed in the future.");
					repos.addAll(m.sources);
				}

				ms.mirrors = new HashMap<>();

				m.source.forEach(r -> {
					if (r.url != null) {
						repos.add(r.url);

						URI mirrorUri = findMirror(r);
						if (mirrorUri != null) {
							getLog().info("Using mirror '" + mirrorUri + "' for " + toString(r));
							ms.mirrors.put(r.url, mirrorUri);
						}
					}
				});

				ms.mirrors.putAll(uriMirrors);

				ms.proxy = findProxy();

				ms.sourceRepositories = repos.toArray(new URI[repos.size()]);
				ms.targetRepository = repoOut;
				ms.offline = session.isOffline() ? OfflineType.offline : OfflineType.online;
				ms.stats = stats;
				ms.filters = m.filters;
				ms.algorithm = m.algorithm;

				byte[] bytes;
				try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					JAXB.marshal(ms, baos);
					baos.flush();
					bytes = baos.toByteArray();
				}

				int exitCode;
				try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
					exitCode = createRunner().run(bais, "-application", "tppt-mirror-application.id1", "-consoleLog",
							"-");
					if (exitCode != 0)
						throw new MojoExecutionException("mirror failed: exitCode=" + exitCode);
				}
			}

		} catch (MojoExecutionException e) {
			throw e;
		} catch (Exception e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}
	}

	private Proxy findProxy() {
		org.apache.maven.settings.Proxy proxy = session.getSettings().getActiveProxy();
		if (proxy == null)
			return null;

		Proxy result = new Proxy();
		result.host = proxy.getHost();
		result.nonProxyHosts = null;
		result.username = proxy.getUsername();
		result.password = proxy.getPassword();
		result.port = proxy.getPort();
		result.protocol = proxy.getProtocol();
		if (proxy.getNonProxyHosts() != null && !proxy.getNonProxyHosts().isEmpty())
			result.nonProxyHosts = Arrays.asList(proxy.getNonProxyHosts().split("[|]", -1));

		return result;
	}

	private List<ArtifactRepository> getPluginRepositories(MavenSession session) {
		List<ArtifactRepository> repositories = new ArrayList<>();
		for (MavenProject project : session.getProjects()) {
			repositories.addAll(project.getPluginArtifactRepositories());
		}
		return repositorySystem.getEffectiveRepositories(repositories);
	}

	private Artifact resolveDependency(MavenSession session, Artifact artifact) throws MavenExecutionException {

		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(artifact);
		request.setResolveRoot(true).setResolveTransitively(false);
		request.setLocalRepository(session.getLocalRepository());
		request.setRemoteRepositories(getPluginRepositories(session));
		request.setOffline(session.isOffline());
		request.setProxies(session.getSettings().getProxies());
		request.setForceUpdate(session.getRequest().isUpdateSnapshots());

		ArtifactResolutionResult result = repositorySystem.resolve(request);

		try {
			resolutionErrorHandler.throwErrors(request, result);
		} catch (ArtifactResolutionException e) {
			throw new MavenExecutionException("Could not resolve artifact for Tycho's OSGi runtime", e);
		}

		return artifact;
	}

	EquinoxRunner runner;

	EquinoxRunner createRunner() throws IOException, MavenExecutionException {
		if (runner == null) {
			Artifact platform = resolveDependency(session,
					repositorySystem.createArtifact("org.eclipse.tycho", "tycho-bundles-external", "1.2.0", "zip"));

			Artifact extra = resolveDependency(session, repositorySystem.createArtifact("com.github.pms1.tppt",
					"tppt-mirror-application", mojoExecution.getVersion(), "jar"));

			Path p = installer.addRuntimeArtifact(session, platform);
			runner = runnerFactory.newBuilder().withInstallation(p).withPlugin(extra.getFile().toPath()).build();
		}
		return runner;
	}
}
