package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.github.pms1.tppt.core.DeploymentHelper;
import com.github.pms1.tppt.core.DeploymentRepository;
import com.github.pms1.tppt.core.DeploymentTarget;
import com.github.pms1.tppt.core.RepositoryPathMatcher;
import com.github.pms1.tppt.core.RepositoryPathPattern;
import com.github.pms1.tppt.p2.CommonP2Repository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.RepositoryComparator;

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
	private RepositoryComparator repositoryComparator;

	@Component
	private P2RepositoryFactory repositoryFactory;

	@Parameter(property = "tppt.deploymentTarget")
	private DeploymentRepository deploymentTarget;

	@Component
	private DeploymentHelper dh;

	@Parameter
	private boolean useBaseline = false;

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (deploymentTarget == null || deploymentTarget.uri == null) {
			getLog().info("No baslineing as 'deploymentTarget' is not set");
			return;
		}

		try (DeploymentTarget dt = dh.createTarget(deploymentTarget)) {

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
					throw new MojoExecutionException("Mixed repositories with and without 'timestamp'");
				}
			}

			if (previous == null)
				getLog().info("No baseline repository found");

			if (useBaseline) {
				if (previous == null) {
					throw new MojoExecutionException("useBaseline, but no baseline repository");
				} else {
					getLog().info("Using baseline from " + previous);

					CommonP2Repository r1 = repositoryFactory.loadAny(dt.getPath().resolve(previous));
					dh.install(r1, target.toPath().resolve("repository"));
				}
			} else {
				if (previous == null) {
				} else {
					getLog().info("Comparing repository to baseline at " + previous);

					CommonP2Repository r1 = repositoryFactory.loadAny(dt.getPath().resolve(previous));
					CommonP2Repository r2 = repositoryFactory.loadAny(target.toPath().resolve("repository"));
					boolean eq = repositoryComparator.run(r1, r2);

					if (eq) {
						getLog().info("Repository is equal to baseline, replacing it");

						dh.replace(r1, r2);
					} else {
						getLog().info("Repository is not equal to baseline");
					}
				}
			}
		} catch (MojoExecutionException e) {
			throw e;
		} catch (IOException e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}

	}

}
