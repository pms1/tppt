package com.github.pms1.tppt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

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
import com.github.pms1.tppt.mirror.MirrorSpec.AuthenticatedUri;
import com.github.pms1.tppt.mirror.MirrorSpec.OfflineType;
import com.github.pms1.tppt.mirror.MirrorSpec.SourceRepository;
import com.github.pms1.tppt.mirror.MirrorSpec.StatsType;
import com.github.pms1.tppt.mirror.Uris;
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

	@Parameter
	private boolean useBaseline = false;

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

		@Parameter
		public String updatePolicy;

		@Parameter(required = true)
		public URI url;

		@Override
		public String toString() {
			return "Repository(id=" + id + ",url=" + url + ")";
		}
	}

	private static final String cacheRelPath = ".cache/tppt/p2";

	private boolean findServers(URI u, Map<Server, Boolean> mirrors, List<AuthenticatedUri> dest) {
		SortedMap<URI, Server> cand = new TreeMap<>();

		for (Server m : mirrors.keySet()) {

			URI mirrorOf;
			try {
				mirrorOf = URI.create(m.getId());
				if (!mirrorOf.isAbsolute())
					continue;
				mirrorOf = Uris.normalizeDirectory(mirrorOf);
			} catch (IllegalArgumentException e) {
				continue;
			}

			if (!Uris.isChild(mirrorOf, u))
				continue;

			Server old = cand.put(mirrorOf, m);
			if (old != null)
				throw new RuntimeException("Settings contain multiple servers for URI '" + u + "'");
		}

		Entry<URI, Server> entry = Uris.findLongestPrefix(cand, u);
		if (entry == null)
			return false;

		mirrors.put(entry.getValue(), true);

		AuthenticatedUri r = new AuthenticatedUri();
		r.uri = Uris.normalizeDirectory(URI.create(entry.getValue().getId()));
		add(entry.getValue(), r, " for URI '" + u + "'");

		dest.add(r);

		return true;
	}

	private boolean findMirrors(URI u, Map<org.apache.maven.settings.Mirror, Boolean> mirrors, MirrorSpec ms) {
		SortedMap<URI, org.apache.maven.settings.Mirror> cand = new TreeMap<>();

		for (org.apache.maven.settings.Mirror m : mirrors.keySet()) {
			if (!Objects.equals(m.getLayout(), "p2") || !Objects.equals(m.getMirrorOfLayouts(), "p2"))
				continue;

			URI mirrorOf;
			try {
				mirrorOf = URI.create(m.getMirrorOf());
				if (!mirrorOf.isAbsolute())
					continue;
				mirrorOf = Uris.normalizeDirectory(mirrorOf);
			} catch (IllegalArgumentException e) {
				continue;
			}

			if (!Uris.isChild(mirrorOf, u))
				continue;

			org.apache.maven.settings.Mirror old = cand.put(mirrorOf, m);
			if (old != null)
				throw new RuntimeException("Settings contain multiple mirrors for URI '" + u + "'");
		}

		Entry<URI, org.apache.maven.settings.Mirror> entry = Uris.findLongestPrefix(cand, u);
		if (entry == null)
			return false;

		mirrors.put(entry.getValue(), true);
		getLog().info("Using mirror '" + entry.getValue().getId() + "' for '" + u + "'");

		AuthenticatedUri r = new AuthenticatedUri();
		r.uri = Uris.normalizeDirectory(URI.create(entry.getValue().getUrl()));
		auth(entry.getValue().getId(), r);
		ms.mirrors.put(Uris.normalizeDirectory(URI.create(entry.getValue().getMirrorOf())), r);

		return true;
	}

	private boolean add(Server match, AuthenticatedUri r, String purpose) {
		if (match == null)
			return false;

		if (match.getUsername() == null || match.getUsername().isEmpty()) {
			getLog().warn("Ignoring server '" + match.getId() + "'" + purpose + " due to missing username");
			return false;
		}

		if (match.getPassword() == null || match.getPassword().isEmpty()) {
			getLog().warn("Ignoring server '" + match.getId() + "'" + purpose + " due to missing password");
			return false;
		}

		r.username = match.getUsername();
		r.password = match.getPassword();

		getLog().info("Using username/password from server '" + match.getId() + "'" + purpose);

		return true;
	}

	private void auth(String id, AuthenticatedUri r) {
		Server match = null;
		for (Server s : session.getSettings().getServers()) {
			if (Objects.equals(id, s.getId())) {
				if (match != null)
					throw new RuntimeException("Settings contain multiple server for id '" + id + "'");
				match = s;
			}
		}

		add(match, r, " for mirror '" + id + "'");
	}

	private boolean findServers(String id, URI uri, Map<Server, Boolean> mirrors, List<AuthenticatedUri> servers2) {
		if (!Uris.isDirectory(uri))
			throw new IllegalArgumentException("Require directory URI: uri=" + uri);

		Server cand = null;

		for (Server m : mirrors.keySet()) {
			if (!Objects.equals(m.getId(), id))
				continue;

			if (cand != null)
				throw new RuntimeException("Settings contain multiple servers for id '" + id + "'");

			cand = m;
		}

		if (cand == null)
			return false;

		getLog().info("Using server '" + cand.getId() + "' for '" + id + "'");

		AuthenticatedUri r = new AuthenticatedUri();
		r.uri = uri;
		add(cand, r, "");

		servers2.add(r);

		return true;
	}

	private boolean findMirrors(String id, final URI uri, Map<org.apache.maven.settings.Mirror, Boolean> mirrors,
			MirrorSpec ms) {
		if (!Uris.isDirectory(uri))
			throw new IllegalArgumentException("Require directory URI: uri=" + uri);

		org.apache.maven.settings.Mirror cand = null;

		for (org.apache.maven.settings.Mirror m : mirrors.keySet()) {
			if (!Objects.equals(m.getLayout(), "p2") || !Objects.equals(m.getMirrorOfLayouts(), "p2"))
				continue;

			if (!Objects.equals(m.getId(), id))
				continue;

			if (cand != null)
				throw new RuntimeException("Settings contain multiple mirrors for id '" + id + "'");

			cand = m;
		}

		if (cand == null)
			return false;

		getLog().info("Using mirror '" + cand.getId() + "' for '" + id + "'");

		AuthenticatedUri r = new AuthenticatedUri();
		r.uri = Uris.normalizeDirectory(URI.create(cand.getUrl()));
		auth(cand.getId(), r);
		ms.mirrors.put(uri, r);

		return true;
	}

	private String toString(Repository r) {
		if (r.id != null && !r.id.isEmpty())
			return "'" + r.id + "'";
		else
			return "at '" + r.url + "'";
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (useBaseline) {
			getLog().info("Skipping due to 'useBaseline'");
			return;
		}
		try {
			final Path repoOut = target.toPath().resolve("repository");

			for (Mirror m : mirrors) {
				MirrorSpec ms = new MirrorSpec();

				Map<Server, Boolean> servers = new HashMap<>();
				session.getSettings().getServers().forEach(s1 -> servers.put(s1, false));
				Map<org.apache.maven.settings.Mirror, Boolean> mirrors = new HashMap<>();
				session.getSettings().getMirrors().forEach(m1 -> mirrors.put(m1, false));

				ms.ius = m.ius.toArray(new String[m.ius.size()]);
				if (m.excludeIus != null)
					ms.excludeIus = m.excludeIus.toArray(new String[m.excludeIus.size()]);
				ms.mirrorRepository = Paths.get(session.getLocalRepository().getBasedir()).resolve(cacheRelPath);
				ms.mirrors = new LinkedHashMap<>();

				List<AuthenticatedUri> servers2 = new ArrayList<>();

				List<SourceRepository> sourceRepositories = new ArrayList<>();
				if (!m.sources.isEmpty()) {
					getLog().warn(
							"Obsolete parameter 'sources' used. Use 'source' instead. 'sources' will be removed in the future.");
					for (URI u : m.sources) {
						u = Uris.normalizeDirectory(u);
						SourceRepository repo = new SourceRepository();
						repo.uri = u;
						sourceRepositories.add(repo);
						findMirrors(u, mirrors, ms);
						findServers(u, servers, servers2);
					}
				}

				m.source.forEach(r -> {
					if (r.url != null) {
						URI u = Uris.normalizeDirectory(r.url);
						SourceRepository repo = new SourceRepository();
						repo.uri = u;
						repo.updatePolicy = r.updatePolicy;
						sourceRepositories.add(repo);

						boolean found = false;

						if (r.id != null && !r.id.isEmpty())
							found = findMirrors(r.id, u, mirrors, ms);

						if (!found)
							found = findMirrors(u, mirrors, ms);

						if (!found)
							found = findServers(r.id, u, servers, servers2);

						if (!found)
							found = findServers(u, servers, servers2);
					}
				});

				mirrors.forEach((m1, added) -> {
					if (!Objects.equals(m1.getLayout(), "p2") || !Objects.equals(m1.getMirrorOfLayouts(), "p2"))
						return;

					URI mirrorOf;
					try {
						mirrorOf = URI.create(m1.getMirrorOf());
						if (!mirrorOf.isAbsolute())
							return;
						mirrorOf = Uris.normalizeDirectory(mirrorOf);
					} catch (IllegalArgumentException e) {
						return;
					}

					getLog().info("Using mirror '" + m1.getId() + "' for transitive dependencies");

					if (!added) {
						AuthenticatedUri r = new AuthenticatedUri();
						r.uri = Uris.normalizeDirectory(URI.create(m1.getUrl()));
						auth(m1.getId(), r);
						ms.mirrors.put(mirrorOf, r);
					}
				});

				servers.forEach((m1, added) -> {

					URI uri;
					try {
						uri = URI.create(m1.getId());
						if (!uri.isAbsolute())
							return;
						uri = Uris.normalizeDirectory(uri);
					} catch (IllegalArgumentException e) {
						return;
					}

					AuthenticatedUri r = new AuthenticatedUri();
					r.uri = uri;
					add(m1, r, " for transitive dependencies");

					if (!added)
						servers2.add(r);
				});

				ms.proxy = findProxy();

				ms.servers = servers2.toArray(new AuthenticatedUri[servers2.size()]);
				ms.sourceRepositories = sourceRepositories.toArray(new SourceRepository[sourceRepositories.size()]);
				ms.targetRepository = repoOut;
				ms.offline = session.isOffline() ? OfflineType.offline : OfflineType.online;
				ms.stats = stats;
				ms.filters = m.filters;
				ms.algorithm = m.algorithm;

				if (getLog().isDebugEnabled())
					JAXB.marshal(ms, System.err);

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
			Artifact platform = resolveDependency(session, repositorySystem.createArtifact("org.eclipse.tycho",
					"tycho-bundles-external", CreateFeaturesMojo.TYCHO_BUNDLES_EXTERNAL_VERSION, "zip"));

			Artifact extra = resolveDependency(session, repositorySystem.createArtifact("com.github.pms1.tppt",
					"tppt-mirror-application", mojoExecution.getVersion(), "jar"));

			Path p = installer.addRuntimeArtifact(session, platform);
			runner = runnerFactory.newBuilder().withInstallation(p).withPlugin(extra.getFile().toPath()).build();
		}
		return runner;
	}
}
