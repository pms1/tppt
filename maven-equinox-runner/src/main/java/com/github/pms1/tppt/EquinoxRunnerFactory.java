package com.github.pms1.tppt;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.IllegalSelectorException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;

@Named
@Singleton
public class EquinoxRunnerFactory {

	@Inject
	private RepositorySystem repositorySystem;

	@Inject
	private ResolutionErrorHandler resolutionErrorHandler;

	@Inject
	private MavenSession session;

	@Inject
	private MavenProject project;

	@Inject
	private MojoExecution execution;

	private Artifact resolveDependency(Artifact artifact) throws MavenExecutionException {
		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(artifact);
		request.setResolveRoot(true);
		request.setResolveTransitively(false);
		request.setLocalRepository(session.getLocalRepository());
		request.setRemoteRepositories(project.getPluginArtifactRepositories());
		request.setOffline(session.isOffline());
		request.setProxies(session.getSettings().getProxies());
		request.setForceUpdate(session.getRequest().isUpdateSnapshots());

		ArtifactResolutionResult result = repositorySystem.resolve(request);

		try {
			resolutionErrorHandler.throwErrors(request, result);
		} catch (ArtifactResolutionException e) {
			throw new MavenExecutionException("Could not resolve artifact for Tycho's OSGi runtime", e);
		}

		return artifact;
	}

	public EquinoxAppRunnerBuilder newBuilderForP2() throws MavenExecutionException {
		return newBuilder() //
				.withFramework("org.eclipse.platform", "org.eclipse.osgi", "3.19.0")
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.common", "3.19.0", 2, true)
				.withBundle("org.apache.felix", "org.apache.felix.scr", "2.2.10", 2, true)
				.withBundle("org.eclipse.platform", "org.eclipse.core.runtime", "3.31.0", 4, true)
				.withBundle("org.osgi", "org.osgi.service.component", "1.5.1", 4, false)
				.withBundle("org.osgi", "org.osgi.util.promise", "1.3.0", 4, false)
				.withBundle("org.osgi", "org.osgi.util.function", "1.2.0", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.core.jobs", "3.15.200", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.registry", "3.12.0", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.preferences", "3.11.0", 4, false)
				.withBundle("org.osgi", "org.osgi.service.prefs", "1.1.2", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.core.contenttype", "3.9.300", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.app", "1.7.0", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.publisher.eclipse", "1.6.0", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.frameworkadmin", "2.3.100", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.frameworkadmin.equinox", "1.3.100", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.artifact.repository", "1.5.300", 4, false)
				.withBundle("org.bouncycastle", "bcpg-jdk18on", "1.77", 4, false)
				.withBundle("org.bouncycastle", "bcprov-jdk18on", "1.77", 4, false)
				.withBundle("org.bouncycastle", "bcutil-jdk18on", "1.77", 4, false)
				.withBundle("org.bouncycastle", "bcpkix-jdk18on", "1.77", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.core", "2.11.0", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.jarprocessor", "1.3.300", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.metadata", "2.9.0", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.metadata.repository", "1.5.300", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.artifact.repository", "1.5.300", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.repository", "2.8.100", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.security", "1.4.200", 4, false)
				.withBundle("org.tukaani", "xz", "1.9", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.publisher", "1.9.100", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.simpleconfigurator.manipulator", "2.3.100", 4,
						false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.simpleconfigurator", "1.5.200", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.osgi.compatibility.state", "1.2.1000", 4, false);
	}

	public EquinoxAppRunnerBuilder newBuilderForP22() throws MavenExecutionException {
		return newBuilderForP2()
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.repository.tools", "2.4.300", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.director", "2.6.300", 4, false)
				.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.engine", "2.8.100", 4, false) //
				.withBundle("org.ow2.sat4j", "org.ow2.sat4j.core", "2.3.6", 4, false)
				.withBundle("org.ow2.sat4j", "org.ow2.sat4j.pb", "2.3.6", 4, false);
	}

	public EquinoxAppRunnerBuilder newBuilder() {
//
//		System.err.println("EXECUTION " + execution);
//		System.err.println("EXECUTION " + execution.getVersion());

		return new EquinoxAppRunnerBuilder() {
			private URL framework;
			private List<AppBundle> bundles = new ArrayList<>();
			private Path launcher;
			private Map<String, String> javaProperties;

			@Override
			public EquinoxAppRunnerBuilder withFramework(String groupId, String artifactId, String version)
					throws MavenExecutionException {

				Artifact artifact = repositorySystem.createArtifact(groupId, artifactId, version, "jar");
				artifact = resolveDependency(artifact);

				try {
					framework = artifact.getFile().toPath().toUri().toURL();
				} catch (MalformedURLException e) {
					throw new MavenExecutionException("Failed to create uri for " + artifact.getFile(), e);
				}

				return this;
			}

			@Override
			public EquinoxAppRunner build() {
				AppRunnerConfig config = new AppRunnerConfig(framework, bundles);

				if (framework == null)
					throw new IllegalSelectorException();

				if (launcher != null)
					throw new UnsupportedOperationException("Not supported at the moment as not fully tested");

				return new EmbeddedEquinoxAppRunner(config);
			}

			@Override
			public EquinoxAppRunnerBuilder withBundle(String groupId, String artifactId, String version,
					Integer startLevel, boolean autoStart) throws MavenExecutionException {

				Artifact artifact = repositorySystem.createArtifact(groupId, artifactId, version, "jar");
				artifact = resolveDependency(artifact);

				try {
					bundles.add(new AppBundle(artifact.getFile().toURI().toURL(), startLevel, autoStart));
				} catch (MalformedURLException e) {
					throw new MavenExecutionException("Failed to create uri for " + artifact.getFile(), e);
				}

				return this;
			}

			@Override
			public EquinoxAppRunnerBuilder withLauncher(String groupId, String artifactId, String version)
					throws MavenExecutionException {
				Artifact artifact = repositorySystem.createArtifact(groupId, artifactId, version, "jar");
				artifact = resolveDependency(artifact);
				launcher = artifact.getFile().toPath();

				return this;
			}

			@Override
			public EquinoxAppRunnerBuilder withJavaProperty(String key, String value) {
				// we should probably quote them instead of refusing
				if (key.contains("!") || key.contains("="))
					throw new IllegalArgumentException();

				if (javaProperties == null)
					javaProperties = new TreeMap<>();
				javaProperties.put(key, value);
				return this;
			}
		};
	}

	public EquinoxAppRunnerBuilder newBuilderForMirror() throws MavenExecutionException {
		return newBuilderForP22() //
				/*
				 * A Maven environment already contains a JAXB implementation which is picked up
				 * by the non-osgified service loader use of jakarta.xml.bind-api. We cannot
				 * prevent an embedded application to "see" that and fail, so we need to launch
				 * an external process.
				 */
				.withLauncher("org.eclipse.platform", "org.eclipse.equinox.launcher", "1.6.700")
				.withBundle(execution.getGroupId(), "tppt-mirror-application", execution.getVersion(), 4, false) //
				// HTTP client
				.withBundle("org.apache.httpcomponents", "httpclient-osgi", "4.5.14", 4, false)
				.withBundle("org.apache.httpcomponents", "httpcore-osgi", "4.4.16", 4, false)
				.withBundle("org.osgi", "org.osgi.service.cm", "1.6.1", 4, false)
				.withBundle("commons-logging", "commons-logging", "1.2", 4, false);
	}

}
