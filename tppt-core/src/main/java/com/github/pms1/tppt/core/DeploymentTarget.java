package com.github.pms1.tppt.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * A {@link DeploymentTarget} provides access via {@link Path} and internally
 * maintains the index.
 * 
 * @author pms1
 *
 */
public interface DeploymentTarget extends Closeable {

	/**
	 * List all repositories. Will scan if not index is present.
	 */
	Collection<Path> findRepositories() throws IOException;

	/**
	 * Return the root path of the deployment target
	 */
	Path getPath();

	void close();

	/**
	 * Add a repository to the index.
	 */
	void addRepository(Path targetRoot) throws IOException;

	/**
	 * Write the index.
	 */
	void writeIndex() throws IOException;
}
