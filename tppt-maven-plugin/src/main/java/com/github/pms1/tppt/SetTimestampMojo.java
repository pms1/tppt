package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.github.pms1.tppt.p2.CommonP2Repository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;

/**
 * A maven mojo to set the timestamps of a p2 repository
 * 
 * @author pms1
 **/
@Mojo(name = "set-timestamp", requiresDependencyResolution = ResolutionScope.COMPILE)
public class SetTimestampMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
	private File target;

	@Component
	private P2RepositoryFactory factory;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

	@Parameter
	private boolean useBaseline;

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (useBaseline) {
			getLog().info("Skipping due to 'useBaseline'");
			return;
		}

		final Path repoOut = target.toPath().resolve("repository");

		try {
			CommonP2Repository p2 = factory.loadAny(repoOut);

			long timestamp = session.getStartTime().getTime();

			p2.getArtifactRepositoryFacade().setTimestamp(timestamp);
			p2.getMetadataRepositoryFacade().setTimestamp(timestamp);

			p2.save();
		} catch (IOException e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}

	}

}
