package com.github.pms1.tppt;

import org.apache.maven.MavenExecutionException;

public interface EquinoxAppRunnerBuilder {

	EquinoxAppRunnerBuilder withFramework(String groupId, String artifactId, String version)
			throws MavenExecutionException;

	EquinoxAppRunnerBuilder withBundle(String groupId, String artifactId, String version, Integer startLevel,
			boolean autoStart) throws MavenExecutionException;

	EquinoxAppRunnerBuilder withLauncher(String groupId, String artifactId, String version)
			throws MavenExecutionException;

	EquinoxAppRunnerBuilder withJavaProperty(String key, String value);

	EquinoxAppRunner build();

}
