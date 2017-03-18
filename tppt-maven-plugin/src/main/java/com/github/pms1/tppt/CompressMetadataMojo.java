package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.github.pms1.tppt.p2.CommonP2Repository;
import com.github.pms1.tppt.p2.DataCompression;
import com.github.pms1.tppt.p2.P2RepositoryFactory;

/**
 * A maven mojo to compress metadata of a p2 repository
 * 
 * @author pms1
 **/
@Mojo(name = "compress-metadata", requiresDependencyResolution = ResolutionScope.COMPILE)
public class CompressMetadataMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
	private File target;

	@Parameter
	private String[] compressions = new String[] { "jar" };

	@Component
	private Map<String, DataCompression> allCompression;

	@Component
	private P2RepositoryFactory factory;

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (compressions == null)
			throw new MojoExecutionException("List of compressions must not be empty.");

		List<DataCompression> comps = new LinkedList<>();

		for (String c : compressions) {
			DataCompression dc = allCompression.get(c);
			if (dc == null)
				throw new MojoExecutionException("Unknown compression '" + c + "'");
			comps.add(dc);
		}

		final Path repoOut = target.toPath().resolve("repository");

		try {
			CommonP2Repository p2 = factory.loadAny(repoOut);
			p2.setCompression(comps.toArray(new DataCompression[comps.size()]));
		} catch (IOException e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}

	}

}
