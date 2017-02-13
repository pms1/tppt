package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.github.pms1.tppt.p2.DataCompression;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.google.common.io.ByteStreams;

/**
 * A maven mojo to compress metadata of a p2 repository
 * 
 * @author pms1
 **/
@Mojo(name = "compress-metadata", requiresDependencyResolution = ResolutionScope.COMPILE)
public class CompressMetadataMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
	private File target;

	@Component
	private Map<String, DataCompression> compressions;

	public void execute() throws MojoExecutionException, MojoFailureException {

		final Path repoOut = target.toPath().resolve("repository");

		try {
			for (String prefix : new String[] { P2RepositoryFactory.ARTIFACT_PREFIX,
					P2RepositoryFactory.METADATA_PREFIX })
				try (InputStream is = compressions.get("xml").openInputStream(repoOut, prefix)) {
					try (OutputStream os = compressions.get("jar").openOutputStream(repoOut, prefix)) {
						ByteStreams.copy(is, os);
					}
				}

			// } catch (MojoExecutionException e) {
			// throw e;
		} catch (IOException e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}

	}

}
