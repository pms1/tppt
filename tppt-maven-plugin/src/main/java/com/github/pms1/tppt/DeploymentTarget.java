package com.github.pms1.tppt;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.maven.plugin.MojoExecutionException;

public class DeploymentTarget {

	private final Path path;

	Path getPath() {
		return path;
	}

	DeploymentTarget(URI deploymentTarget) throws MojoExecutionException {
		if (deploymentTarget.getScheme() == null || !deploymentTarget.isAbsolute())
			throw new MojoExecutionException("The deploymentTarget '" + deploymentTarget + "' is not a valid URI.");

		switch (deploymentTarget.getScheme()) {
		case "file":
			path = Paths.get(deploymentTarget);
			if (Files.exists(path) && !Files.isDirectory(path))
				throw new MojoExecutionException("The path '" + path + "' already exists and is not a directory");
			try {
				Files.createDirectories(path);
			} catch (IOException e1) {
				throw new MojoExecutionException("Failed to create the path '" + path + "'", e1);
			}
			break;
		default:
			throw new MojoExecutionException("The scheme '" + deploymentTarget.getScheme() + "' of deploymentTarget '"
					+ deploymentTarget + "' is not supported.");
		}

	}

	public Collection<Path> findRepositories() throws IOException {
		Collection<Path> result = new ArrayList<>();

		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (Files.exists(dir.resolve("p2.index")))
					result.add(path.relativize(dir));

				return super.preVisitDirectory(dir, attrs);
			}
		});

		return result;

	}
}
