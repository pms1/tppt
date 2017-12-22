package com.github.pms1.tppt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

import com.github.pms1.tppt.mirror.MirrorSpec;
import com.github.pms1.tppt.mirror.MirrorSpec.AlgorithmType;
import com.github.pms1.tppt.mirror.MirrorSpec.OfflineType;
import com.github.pms1.tppt.mirror.MirrorSpec.StatsType;
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

		@Parameter
		public List<URI> sources;

		@SuppressWarnings("unchecked")
		@Parameter
		public Map<String, String>[] filters = new Map[0];

		@Parameter
		public AlgorithmType algorithm = AlgorithmType.permissiveSlicer;
	}

	private static final String cacheRelPath = ".cache/tppt/p2";

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			final Path repoOut = target.toPath().resolve("repository");

			for (Mirror m : mirrors) {
				MirrorSpec ms = new MirrorSpec();

				ms.ius = m.ius.toArray(new String[m.ius.size()]);
				if (m.excludeIus != null)
					ms.excludeIus = m.excludeIus.toArray(new String[m.excludeIus.size()]);
				ms.mirrorRepository = Paths.get(session.getLocalRepository().getBasedir()).resolve(cacheRelPath);
				ms.sourceRepositories = m.sources.toArray(new URI[m.sources.size()]);
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
					repositorySystem.createArtifact("org.eclipse.tycho", "tycho-bundles-external", "1.0.0", "zip"));

			Artifact extra = resolveDependency(session, repositorySystem.createArtifact("com.github.pms1.tppt",
					"tppt-mirror-application", mojoExecution.getVersion(), "jar"));

			Path p = installer.addRuntimeArtifact(session, platform);
			runner = runnerFactory.newBuilder().withInstallation(p).withPlugin(extra.getFile().toPath()).build();
		}
		return runner;
	}
}
