package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExclusionSetFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

import com.github.pms1.tppt.jaxb.Plugin;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;

import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.version.Version;

/**
 * A maven mojo for creating a p2 repository from maven dependencies
 * 
 * @author pms1
 **/
@Mojo(name = "create-from-dependencies", requiresDependencyResolution = ResolutionScope.COMPILE)
public class CreateFromDependenciesMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
	private File target;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${mojoExecution}", readonly = true)
	private MojoExecution mojoExecution;

	@Component
	private RepositorySystem repositorySystem;

	@Component
	private ResolutionErrorHandler resolutionErrorHandler;

	@Parameter(readonly = true, required = true, defaultValue = "${project.basedir}/src/main/bnd")
	private File sourceDir;

	@Inject
	private EquinoxRunnerFactory appRunnerFactory;

	@Component(hint = "default")
	private DependencyGraphBuilder dependencyGraphBuilder;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

	@Parameter
	private ArtifactFilter exclusionTransitives = new ExclusionSetFilter(Collections.emptySet());

	@Parameter
	private boolean useBaseline;

	public void setExclusionTransitives(String[] exclusionTransitives) {
		this.exclusionTransitives = new ExclusionSetFilter(exclusionTransitives);
	}

	enum RepositoryDependenciesBehaviour {
		failure, ignore, include;
	}

	@Parameter(defaultValue = "failure")
	private RepositoryDependenciesBehaviour repositoryDependencies;

	@Parameter
	private ArtifactFilter exclusions = new ExclusionSetFilter(Collections.emptySet());

	public void setExclusions(String[] exclusions) {
		this.exclusions = new ExclusionSetFilter(exclusions);
	}

	static Plugin scanPlugin(Path p) throws IOException, BundleException, MojoExecutionException {
		long uncompressedSize = 0;
		Map<String, String> manifest = null;

		// Cannot use ZipInputStream since that leaves getCompressedSize() and
		// getSize() of ZipEntry unfilled
		try (ZipFile zf = new ZipFile(p.toFile())) {
			for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
				ZipEntry entry = e.nextElement();

				if (entry.getSize() == -1)
					throw new Error();
				uncompressedSize += entry.getSize();

				boolean isManifest = entry.getName().equals("META-INF/MANIFEST.MF");

				if (isManifest) {
					if (manifest != null)
						throw new IllegalStateException();

					manifest = ManifestElement.parseBundleManifest(zf.getInputStream(entry), null);
				}
			}
		}

		if (manifest == null)
			return null;

		ManifestElement[] elements = ManifestElement.parseHeader(Constants.BUNDLE_SYMBOLICNAME,
				manifest.get(Constants.BUNDLE_SYMBOLICNAME));
		if (elements == null)
			return null;

		if (elements.length != 1)
			throw new MojoExecutionException(
					"Unhandled: malformed " + Constants.BUNDLE_SYMBOLICNAME + " header: " + Arrays.toString(elements));
		Plugin result = new Plugin();
		result.id = elements[0].getValue();

		elements = ManifestElement.parseHeader(Constants.BUNDLE_VERSION, manifest.get(Constants.BUNDLE_VERSION));
		if (elements.length != 1)
			throw new MojoExecutionException(
					"Unhandled: malformed " + Constants.BUNDLE_VERSION + " header: " + Arrays.toString(elements));
		result.version = elements[0].getValue();
		result.download_size = Files.size(p) / 1024;
		result.install_size = uncompressedSize / 1024;

		return result;
	}

	/**
	 * Headers that are in regular bundles, but must not be in source bundles.
	 */
	private final static List<Name> binaryHeaders;
	static {
		List<Name> names = new LinkedList<>();

		names.add(Name.MAIN_CLASS);

		Arrays.asList(Constants.IMPORT_PACKAGE, //
				Constants.REQUIRE_BUNDLE, //
				Constants.REQUIRE_CAPABILITY, //
				Constants.PROVIDE_CAPABILITY, //
				Constants.EXPORT_PACKAGE) //
				.stream() //
				.map(p -> new Name(p)) //
				.forEach(names::add);

		binaryHeaders = Collections.unmodifiableList(names);
	}

	// Implementation-Title: hibernate-core
	// Implementation-Version: 5.2.3.Final
	// Specification-Vendor: Hibernate.org
	// Specification-Title: hibernate-core
	// Implementation-Vendor-Id: org.hibernate
	// Implementation-Vendor: Hibernate.org
	// Bundle-Name: hibernate-core
	// Bundle-Version: 5.2.3.Final
	// Specification-Version: 5.2.3.Final
	// Implementation-Url: http://hibernate.org

	private static class WrappedMojoExecutionException extends RuntimeException {
		WrappedMojoExecutionException(MojoExecutionException cause) {
			super(cause);
		}

		@Override
		public synchronized MojoExecutionException getCause() {
			return (MojoExecutionException) super.getCause();
		}
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (useBaseline) {
			getLog().info("Skipping due to 'useBaseline'");
			return;
		}

		final Path repoDependencies = target.toPath().resolve("repository-source");
		final Path repoDependenciesPlugins = repoDependencies.resolve("plugins");

		final Path repoOut = target.toPath().resolve("repository");

		try {
			Files.createDirectories(repoDependenciesPlugins);

			Map<File, Artifact> reactorRepositories = new HashMap<>();

			for (MavenProject p : session.getProjects()) {
				if (p == project)
					continue;

				switch (p.getPackaging()) {
				case "tppt-repository":
				case "tppt-composite-repository":
					break;
				default:
					continue;
				}

				// We can safely ignore artifacts that are not build yet
				if (p.getArtifact() == null || p.getArtifact().getFile() == null)
					continue;

				reactorRepositories.put(p.getArtifact().getFile(), p.getArtifact());
			}

			ProjectBuildingRequest pbRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
			pbRequest.setProject(project);
			pbRequest.setResolveDependencies(true);

			DependencyNode n = dependencyGraphBuilder.buildDependencyGraph(pbRequest, null);

			Set<Artifact> artifacts = new HashSet<>();

			Set<Artifact> repoArtifacts = new HashSet<>();

			n.accept(new DependencyNodeVisitor() {
				@Override
				public boolean visit(DependencyNode node) {
					// ourselves
					if (node.getParent() == null)
						return true;

					Artifact a;
					try {
						a = resolveDependency(node.getArtifact());
					} catch (MavenExecutionException e) {
						throw new Error(e);
					}

					Artifact reactorDependency = reactorRepositories.get(a.getFile());
					if (reactorDependency != null) {
						switch (repositoryDependencies) {
						case failure:
							throw new WrappedMojoExecutionException(
									new MojoExecutionException("Repository dependency to " + reactorDependency));
						case ignore:
							return false;
						case include:
							repoArtifacts.add(a);
							return false;
						default:
							throw new UnsupportedOperationException();
						}
					}

					if (exclusions.include(node.getArtifact()))
						artifacts.add(a);

					return exclusionTransitives.include(node.getArtifact());
				}

				@Override
				public boolean endVisit(DependencyNode node) {
					return true;
				}
			});

			final String buildQualifier = project.getProperties().getProperty("buildQualifier");
			if (Strings.isNullOrEmpty(buildQualifier))
				throw new MojoExecutionException("Project does not have build qualifier set");

			for (Artifact a : artifacts) {
				Plugin plugin = scanPlugin(a.getFile().toPath());
				Path receipe = findReceipe(a);

				if (!a.getFile().getName().endsWith(".jar"))
					throw new MojoExecutionException(
							"Unhandled dependency to non JAR file: " + a.getFile() + " from " + a);

				if (plugin == null || receipe != null) {
					plugin = createPlugin(a, plugin, buildQualifier, receipe,
							repoDependenciesPlugins.resolve(a.getFile().toPath().getFileName()));
				} else {
					Files.copy(a.getFile().toPath(),
							repoDependenciesPlugins.resolve(a.getFile().toPath().getFileName()));
				}

				if (plugin == null)
					throw new Error();

				// try to find artifacts with "sources" qualifier
				Artifact sourcesArtifact = repositorySystem.createArtifactWithClassifier(a.getGroupId(),
						a.getArtifactId(), a.getVersion(), "jar", "sources");
				ArtifactResolutionRequest request = new ArtifactResolutionRequest();
				request.setArtifact(sourcesArtifact);
				request.setLocalRepository(session.getLocalRepository());
				request.setRemoteRepositories(project.getPluginArtifactRepositories());
				ArtifactResolutionResult resolution = repositorySystem.resolve(request);

				switch (resolution.getArtifacts().size()) {
				case 0:
					// no sources: sad, but ok
					break;
				case 1:
					Artifact sourceArtifact = Iterables.getOnlyElement(resolution.getArtifacts());

					Path out1 = repoDependenciesPlugins.resolve(sourceArtifact.getFile().toPath().getFileName());

					createSourceBundle(plugin, sourceArtifact.getFile().toPath(), out1);

					break;
				default:
					throw new MojoExecutionException(
							"Unhandled: multiple source artifacts: " + resolution.getArtifacts());
				}
			}

			int exitCode = createRunner().run("org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher", //
					"-source", repoDependencies.toString(), //
					"-metadataRepository", repoOut.toUri().toURL().toExternalForm(), //
					"-artifactRepository", repoOut.toUri().toURL().toExternalForm(), //
					"-publishArtifacts", //
					"-append", "true", //
					"-metadataRepositoryName", project.getName(), //
					"-artifactRepositoryName", project.getName());

			if (exitCode != 0)
				throw new MojoExecutionException("Running p2 failed: exitCode=" + exitCode);

			for (Artifact r : repoArtifacts) {
				exitCode = createRunner2().run("org.eclipse.equinox.p2.metadata.repository.mirrorApplication", //
						"-source", "jar:" + r.getFile().toURI() + "!/", "-destination",
						repoOut.toUri().toURL().toExternalForm());
				if (exitCode != 0)
					throw new MojoExecutionException("Running p2 failed: exitCode=" + exitCode);

				exitCode = createRunner2().run("org.eclipse.equinox.p2.artifact.repository.mirrorApplication", //
						"-source", "jar:" + r.getFile().toURI() + "!/", "-destination",
						repoOut.toUri().toURL().toExternalForm());
				if (exitCode != 0)
					throw new MojoExecutionException("Running p2 failed: exitCode=" + exitCode);
			}

			Files.write(repoOut.resolve("p2.index"),
					"version = 1\rmetadata.repository.factory.order = content.xml,\\!\rartifact.repository.factory.order = artifacts.xml,\\!\r"
							.getBytes(StandardCharsets.US_ASCII));

		} catch (WrappedMojoExecutionException e) {
			throw e.getCause();
		} catch (MojoExecutionException e) {
			throw e;
		} catch (Exception e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}
	}

	private void createSourceBundle(Plugin plugin, Path bundle, Path out1) throws Exception {

		Manifest mf = null;

		try (ZipFile zf = new ZipFile(bundle.toFile())) {
			ZipEntry e = zf.getEntry("META-INF/MANIFEST.MF");
			if (e != null)
				mf = new Manifest(zf.getInputStream(e));
		}

		boolean sourceHasHeaders = false;

		if (mf != null) {
			String sourceBundle = mf.getMainAttributes().getValue("Eclipse-SourceBundle");

			if (sourceBundle != null) {
				ManifestElement[] elements = ManifestElement.parseHeader("Eclipse-SourceBundle", sourceBundle);
				if (elements.length != 1)
					throw new MojoExecutionException(
							"Unhandled: malformed Eclipse-SourceBundle header: " + sourceBundle);
				String sbundle = elements[0].getValue();
				String sversion = elements[0].getAttribute("version");
				if (Objects.equals(plugin.id, sbundle) && Objects.equals(plugin.version, sversion))
					sourceHasHeaders = true;
			}
		}

		boolean requireCleanup = false;

		if (mf != null) {

			Manifest fmf = mf;
			requireCleanup = binaryHeaders.stream().anyMatch(p -> fmf.getMainAttributes().getValue(p) != null);

		}
		if (sourceHasHeaders && !requireCleanup) {
			Files.copy(bundle, out1);
		} else {
			if (mf == null) {
				mf = new Manifest();
				// This is a required header
				mf.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
			}
			for (Name h : binaryHeaders)
				mf.getMainAttributes().remove(h);
			mf.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
			mf.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, plugin.id + ".source");
			mf.getMainAttributes().putValue(Constants.BUNDLE_VERSION, plugin.version);
			mf.getMainAttributes().putValue("Eclipse-SourceBundle", plugin.id + ";version=\"" + plugin.version + "\"");

			try (JarFile zf = new JarFile(bundle.toFile());
					OutputStream os = Files.newOutputStream(out1);
					JarOutputStream jar = new JarOutputStream(os, mf)) {

				Set<String> names = new HashSet<>();

				for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
					ZipEntry e1 = e.nextElement();
					if (e1.getName().equals("META-INF/MANIFEST.MF"))
						continue;

					if (names.add(e1.getName())) {
						jar.putNextEntry(new JarEntry(e1.getName()));

						long copied = ByteStreams.copy(zf.getInputStream(e1), jar);

						if (e1.getSize() != -1 && copied != e1.getSize())
							throw new MojoExecutionException("Error while copying entry '" + e1 + "': size should be "
									+ e1.getSize() + ", but copied " + copied);
					} else {
						getLog().warn(bundle + " contains multiple entries for '" + e1.getName()
								+ "'. Keeping the first and ignoring subsequent entries with the same name.");
					}
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Failed creating source bundle '" + out1 + "' from '" + bundle + "'",
						e);
			}
		}
	}

	private Path findReceipe(Artifact a) {
		Path noVersionPath = sourceDir.toPath().resolve(a.getGroupId()).resolve(a.getArtifactId());
		if (a.hasClassifier())
			noVersionPath = noVersionPath.resolve(a.getClassifier());
		Path versionPath = noVersionPath.resolve(a.getVersion());

		for (Path path : new Path[] { versionPath, noVersionPath }) {
			Path bndFile = path.resolve("bnd.bnd");

			if (Files.isReadable(bndFile))
				return bndFile;
		}

		return null;
	}

	private String createSymbolicName(Artifact a) {
		if (a.hasClassifier())
			return a.getArtifactId() + "." + a.getClassifier();
		else
			return a.getArtifactId();
	}

	private Plugin createPlugin(Artifact a, Plugin plugin, String buildQualifer, Path receipe, Path target)
			throws Exception {
		try (Builder builder = new Builder()) {
			builder.setTrace(getLog().isDebugEnabled());

			Jar classesDirJar = new Jar(a.getFile());

			if (receipe != null)
				builder.setProperties(receipe.getParent().toFile(), builder.loadProperties(receipe.toFile()));

			if (receipe != null)
				getLog().info(a + ": Creating an OSGi bundle using '" + sourceDir.toPath().relativize(receipe) + "'");
			else
				getLog().info(a + ": Creating an OSGi bundle");

			if (builder.getProperty(Constants.BUNDLE_SYMBOLICNAME) == null)
				if (plugin != null)
					builder.setProperty(Constants.BUNDLE_SYMBOLICNAME, plugin.id);
				else
					builder.setProperty(Constants.BUNDLE_SYMBOLICNAME, createSymbolicName(a));

			if (builder.getProperty(Constants.BUNDLE_VERSION) == null) {
				Version v;
				if (plugin != null)
					v = Version.parseVersion(plugin.version);
				else
					v = CreateFeaturesMojo.createOsgiVersion(a.getVersion());
				v = new Version(v.getMajor(), v.getMinor(), v.getMicro(), buildQualifer);
				builder.setProperty(Constants.BUNDLE_VERSION, v.toString());
			}

			if (builder.getProperty(Constants.EXPORT_PACKAGE) == null)
				builder.setProperty(Constants.EXPORT_PACKAGE, "*");

			builder.setJar(classesDirJar);

			Jar j = builder.build();
			j.write(target.toFile());

			return scanPlugin(target);
		}
	}

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
			throw new MavenExecutionException("Could not resolve artifact: " + artifact, e);
		}

		return artifact;
	}

	private EquinoxAppRunner appRunner;

	EquinoxAppRunner createRunner() throws IOException, MavenExecutionException {
		if (appRunner == null)
			appRunner = appRunnerFactory.newBuilderForP2().build();
		return appRunner;
	}

	private EquinoxAppRunner appRunner2;

	EquinoxAppRunner createRunner2() throws IOException, MavenExecutionException {
		if (appRunner2 == null)
			appRunner2 = appRunnerFactory.newBuilderForP22()
					.withBundle("org.eclipse.platform", "org.eclipse.equinox.p2.transport.ecf", "1.4.200", 4, false) //
					// ECF + dependencies
					.withBundle("org.eclipse.ecf", "org.eclipse.ecf", "3.11.0", 4, false) //
					.withBundle("org.eclipse.ecf", "org.eclipse.ecf.identity", "3.10.0", 4, false) //
					.withBundle("org.eclipse.ecf", "org.eclipse.ecf.filetransfer", "5.1.103", 4, false) //
					.withBundle("org.eclipse.ecf", "org.eclipse.ecf.provider.filetransfer", "3.3.0", 4, false) //
					.withBundle("org.eclipse.platform", "org.eclipse.equinox.concurrent", "1.3.0", 4, false) //
					.build();
		return appRunner2;
	}

}
