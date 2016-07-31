package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;

/**
 * A maven mojo to replace a p2 repository by it's baseline
 * 
 * @author pms1
 **/
@Mojo(name = "replace-by-baseline", requiresDependencyResolution = ResolutionScope.COMPILE)
public class BaselineMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
	private File target;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Component
	private RepositorySystem repositorySystem;

	@Parameter(readonly = true, required = true, defaultValue = "${project.remoteArtifactRepositories}")
	private List<ArtifactRepository> remoteArtifactRepositories;

	@Parameter(readonly = true, required = true, defaultValue = "${localRepository}")
	private ArtifactRepository localRepository;

	@Component
	private EquinoxRunnerFactory runnerFactory;

	@Component
	private TychoArtifactUnpacker installer;

	@Component
	private ResolutionErrorHandler resolutionErrorHandler;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

	@Parameter(property = "tppt.deploymentTarget")
	private URI deploymentTarget;

	DeploymentHelper dh = new DeploymentHelper();

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (deploymentTarget == null) {
			getLog().info("No baslineing as 'deploymentTarget' is not set");
			return;
		}

		try {

			System.err.println("DT " + deploymentTarget);

			DeploymentTarget dt = new DeploymentTarget(deploymentTarget);

			Collection<Path> repositories = dt.findRepositories();
			RepositoryPathPattern pattern = dh.getPathPattern(project);

			Path previous = null;
			LocalDateTime previousTimestamp = null;

			for (Path p : repositories) {
				RepositoryPathMatcher m = pattern.matcher(p);
				if (!m.matches())
					continue;

				LocalDateTime timestamp = m.get("timestamp", LocalDateTime.class);
				if (previous == null) {
					previous = p;
					previousTimestamp = timestamp;
				} else if (previousTimestamp != null && timestamp != null) {
					if (timestamp.isAfter(previousTimestamp)) {
						previous = p;
						previousTimestamp = timestamp;
					}
				} else {
					throw new MojoExecutionException("mixed repositories with and without 'timestamp'");
				}
			}

			System.err.println("Comaparing to baseline at " + previous);

			Object ctxv = project.getContextValue("key");
			System.err.println("Comaparing to baseline at " + ctxv);
		} catch (MojoExecutionException e) {
			throw e;
		} catch (IOException e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}

	}

}
