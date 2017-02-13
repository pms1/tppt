package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import com.github.pms1.tppt.core.RepositoryPathMatcher;
import com.github.pms1.tppt.core.RepositoryPathPattern;
import com.github.pms1.tppt.p2.P2Repository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.RepositoryComparator;
import com.github.pms1.tppt.p2.RepositoryDataCompressionChange;

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
	private URI deploymentTarget;

	@Component
	private DeploymentHelper dh;

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (deploymentTarget == null) {
			getLog().info("No baslineing as 'deploymentTarget' is not set");
			return;
		}

		try {
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

			if (previous == null) {
				getLog().info("No baseline repository found");
			} else {
				getLog().info("Comparing repository to baseline at " + previous);

				P2Repository r1 = repositoryFactory.create(dt.getPath().resolve(previous));
				P2Repository r2 = repositoryFactory.create(target.toPath().resolve("repository"));
				boolean eq = repositoryComparator.run(r1, r2, RepositoryDataCompressionChange::new);

				if (eq) {
					getLog().info("Repository is equal to baseline, replacing it");

					dh.replace(r1, r2);
				} else {
					getLog().info("Repository is not equal to baseline");
				}
			}
		} catch (MojoExecutionException e) {
			throw e;
		} catch (IOException e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}

	}

}
