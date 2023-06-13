package com.github.pms1.tppt;

import java.io.IOException;
import java.nio.file.Path;

public interface EquinoxRunnerBuilder {

	/**
	 * @param path
	 * @return {@code this}
	 */
	EquinoxRunnerBuilder withInstallation(Path path);

	/**
	 * Add a Java property to the executed VM.
	 * 
	 * @return {@code this}
	 */
	EquinoxRunnerBuilder withJavaProperty(String key, String value);

	EquinoxRunner build() throws IOException;

	EquinoxRunnerBuilder withPlugin(Path path) throws IOException;

}
