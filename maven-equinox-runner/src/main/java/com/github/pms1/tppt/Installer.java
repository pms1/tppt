package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.FileUtils;

import com.github.pms1.tppt.TychoUnpackLocker.Lock;

@Component(role = Installer.class)
public class Installer {

	@Requirement
	private RepositorySystem repositorySystem;

	@Requirement
	private ResolutionErrorHandler resolutionErrorHandler;

	@Requirement
	private Logger logger;

	@Requirement(hint = "zip")
	private UnArchiver unArchiver;

	protected List<ArtifactRepository> getPluginRepositories(MavenSession session) {
		List<ArtifactRepository> repositories = new ArrayList<>();
		for (MavenProject project : session.getProjects()) {
			repositories.addAll(project.getPluginArtifactRepositories());
		}
		return repositorySystem.getEffectiveRepositories(repositories);
	}

	public Artifact resolveDependency(MavenSession session, Dependency dependency) throws MavenExecutionException {
		Artifact artifact = repositorySystem.createArtifact(dependency.getGroupId(), dependency.getArtifactId(),
				dependency.getVersion(), dependency.getType());

		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(artifact);
		request.setResolveRoot(true).setResolveTransitively(false);
		request.setLocalRepository(session.getLocalRepository());
		request.setRemoteRepositories(getPluginRepositories(session));
		request.setCache(session.getRepositoryCache());
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

	public Path addRuntimeArtifact(MavenSession session, Dependency dependency)
			throws MavenExecutionException, IOException {
		Artifact artifact = resolveDependency(session, dependency);

		File artifactFile = new File(session.getLocalRepository().getBasedir(),
				session.getLocalRepository().pathOf(artifact));
		File eclipseDir = new File(artifactFile.getParentFile(), "eclipse");

		try (Lock lock = new TychoUnpackLocker().lock(artifactFile.toPath())) {
			if (!eclipseDir.exists() || artifact.isSnapshot()) {
				logger.debug("Extracting Tycho's OSGi runtime");

				if (artifact.getFile().lastModified() > eclipseDir.lastModified()) {
					logger.debug("Unpacking Tycho's OSGi runtime to " + eclipseDir);
					try {
						FileUtils.deleteDirectory(eclipseDir);
					} catch (IOException e) {
						logger.warn("Failed to delete Tycho's OSGi runtime " + eclipseDir + ": " + e.getMessage());
					}
					unArchiver.setSourceFile(artifact.getFile());
					unArchiver.setDestDirectory(eclipseDir.getParentFile());
					try {
						unArchiver.extract();
					} catch (ArchiverException e) {
						throw new MavenExecutionException("Failed to unpack Tycho's OSGi runtime: " + e.getMessage(),
								e);
					}

					eclipseDir.setLastModified(artifact.getFile().lastModified());
				}
			}
		}

		return eclipseDir.toPath();
	}
}
