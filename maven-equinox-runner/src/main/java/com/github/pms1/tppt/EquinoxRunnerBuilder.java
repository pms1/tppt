package com.github.pms1.tppt;

import java.io.IOException;
import java.nio.file.Path;

public interface EquinoxRunnerBuilder {

	/**
	 * @param path
	 * @return {@code this}
	 */
	EquinoxRunnerBuilder withInstallation(Path path);

	EquinoxRunner build() throws IOException;

	EquinoxRunnerBuilder withPlugin(Path path) throws IOException;

}
