package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.bind.JAXB;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExclusionSetFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
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

import com.github.pms1.tppt.jaxb.Feature;
import com.github.pms1.tppt.jaxb.Plugin;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;

import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.version.MavenVersion;
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

	@Component
	private RepositorySystem repositorySystem;

	@Parameter(readonly = true, required = true, defaultValue = "${project.remoteArtifactRepositories}")
	private List<ArtifactRepository> remoteRepositories;

	@Parameter(readonly = true, required = true, defaultValue = "${localRepository}")
	private ArtifactRepository localRepository;

	@Component
	private EquinoxRunnerFactory runnerFactory;

	@Component
	private TychoArtifactUnpacker installer;

	@Component
	private ResolutionErrorHandler resolutionErrorHandler;

	@Component(hint = "default")
	private DependencyGraphBuilder dependencyGraphBuilder;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

	@Parameter
	private ArtifactFilter exclusionTransitives = new ExclusionSetFilter(Collections.emptySet());

	public void setExclusionTransitives(String[] exclusionTransitives) {
		this.exclusionTransitives = new ExclusionSetFilter(exclusionTransitives);
	}

	@Parameter
	private ArtifactFilter exclusions = new ExclusionSetFilter(Collections.emptySet());

	public void setExclusions(String[] exclusions) {
		this.exclusions = new ExclusionSetFilter(exclusions);
	}

	static Plugin scanPlugin(Path p) throws IOException, BundleException, MojoExecutionException {
		long compressedSize = 0;
		long uncompressedSize = 0;
		Map<String, String> manifest = null;

		// Cannot use ZipInputStream since that leaves getCompressedSize() and
		// getSize() of ZipEntry unfilled
		try (ZipFile zf = new ZipFile(p.toFile())) {
			for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
				ZipEntry entry = e.nextElement();

				if (entry.getCompressedSize() == -1)
					throw new Error();
				compressedSize += entry.getCompressedSize();

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
			throw new MojoExecutionException("FIXME");
		Plugin result = new Plugin();
		result.id = elements[0].getValue();

		elements = ManifestElement.parseHeader(Constants.BUNDLE_VERSION, manifest.get(Constants.BUNDLE_VERSION));
		if (elements.length != 1)
			throw new MojoExecutionException("FIXME");
		result.version = elements[0].getValue();
		result.download_size = compressedSize / 1024;
		result.install_size = uncompressedSize / 1024;
		result.unpack = true;

		return result;
	}

	private final static List<Name> binaryHeaders = Arrays
			.asList(Constants.IMPORT_PACKAGE, //
					Constants.REQUIRE_BUNDLE, //
					Constants.REQUIRE_CAPABILITY, //
					Constants.PROVIDE_CAPABILITY, //
					Constants.EXPORT_PACKAGE, //
					"Main-Class") // FIXME: use Name.xxx constant
			.stream() //
			.map(p -> new Name(p)) //
			.collect(Collectors.toList());

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

	public void execute() throws MojoExecutionException, MojoFailureException {
		final String qualifiedVersion = project.getProperties().getProperty("qualifiedVersion");
		if (Strings.isNullOrEmpty(qualifiedVersion))
			throw new MojoExecutionException("FIXME");

		final Path repoDependencies = target.toPath().resolve("repository-source");
		final Path repoDependenciesPlugins = repoDependencies.resolve("plugins");

		final Path repoFeatures = target.toPath().resolve("repository-features");
		final Path repoFeaturesFeatures = repoFeatures.resolve("features");

		final Path repoOut = target.toPath().resolve("repository");

		try {
			Files.createDirectories(repoDependenciesPlugins);

			List<Plugin> plugins = new LinkedList<>();

			ProjectBuildingRequest pbRequest = new DefaultProjectBuildingRequest();
			pbRequest.setLocalRepository(localRepository);
			pbRequest.setProject(project);
			pbRequest.setRemoteRepositories(remoteRepositories);
			pbRequest.setRepositorySession(session.getRepositorySession());
			pbRequest.setResolveDependencies(true);
			pbRequest.setResolveVersionRanges(true);

			DependencyNode n = dependencyGraphBuilder.buildDependencyGraph(pbRequest, null);

			Set<Artifact> artifacts = new HashSet<>();

			n.accept(new DependencyNodeVisitor() {
				@Override
				public boolean visit(DependencyNode node) {
					if (node.getArtifact() != project.getArtifact() && exclusions.include(node.getArtifact())) {
						// the node artifact is not resolved, so we search the
						// resolved version.
						// we cannot resolve them later as some artifacts are
						// not resolveable
						// standalone (e.g. if they are not in maven central but
						// a repository
						// in their parent).
						Set<Artifact> collect = project.getArtifacts().stream()
								.filter(p -> p.toString().equals(node.getArtifact().toString()))
								.collect(Collectors.toSet());
						if (collect.size() != 1)
							throw new Error();
						artifacts.addAll(collect);
					}

					return exclusionTransitives.include(node.getArtifact());
				}

				@Override
				public boolean endVisit(DependencyNode node) {
					return true;
				}
			});

			for (Artifact a : artifacts) {
				Plugin plugin = scanPlugin(a.getFile().toPath());
				if (plugin == null) {
					getLog().info("Dependency is not an OSGi bundle: " + a);
					plugin = createPlugin(a, repoDependenciesPlugins.resolve(a.getFile().toPath().getFileName()));
				} else {
					Files.copy(a.getFile().toPath(),
							repoDependenciesPlugins.resolve(a.getFile().toPath().getFileName()));
				}

				if (plugin == null)
					throw new Error();

				plugins.add(plugin);

				// try to find artifacts with "sources" qualifier
				Artifact sourcesArtifact = repositorySystem.createArtifactWithClassifier(a.getGroupId(),
						a.getArtifactId(), a.getVersion(), "jar", "sources");
				ArtifactResolutionRequest request = new ArtifactResolutionRequest();
				request.setArtifact(sourcesArtifact);
				request.setRemoteRepositories(remoteRepositories);
				request.setLocalRepository(localRepository);
				ArtifactResolutionResult resolution = repositorySystem.resolve(request);

				switch (resolution.getArtifacts().size()) {
				case 0:
					// no sources: sad, but ok
					break;
				case 1:
					Artifact sourceArtifact = Iterables.getOnlyElement(resolution.getArtifacts());

					Path out1 = repoDependenciesPlugins.resolve(sourceArtifact.getFile().toPath().getFileName());

					createSourceBundle(plugin, sourceArtifact.getFile().toPath(), out1);

					plugins.add(scanPlugin(out1));

					break;
				default:
					for (Artifact a2 : resolution.getArtifacts()) {
						System.err.println("SOURCE " + a2 + " " + a2.isResolved() + " " + a2.getFile());
					}
					throw new Error("FIXME");
				}
			}

			int exitCode = createRunner().run("-application",
					"org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher", "-source",
					repoDependencies.toString(), //
					"-metadataRepository", repoOut.toUri().toURL().toExternalForm(), //
					"-artifactRepository", repoOut.toUri().toURL().toExternalForm(), //
					"-publishArtifacts" //
			// "-metadataRepositoryName", "foo1", "-artifactRepositoryName",
			// "bar"
			);

			// ** create and publish feature
			Feature f = new Feature();
			f.label = project.getName(); // null is ok
			f.id = project.getArtifactId();
			f.version = qualifiedVersion;
			f.plugins = plugins.toArray(new Plugin[plugins.size()]);

			Files.createDirectories(repoFeaturesFeatures);

			try (OutputStream os = Files.newOutputStream(repoFeaturesFeatures.resolve("feature.jar"));
					JarOutputStream jar = new JarOutputStream(os)) {
				JarEntry je = new JarEntry("feature.xml");

				jar.putNextEntry(je);
				JAXB.marshal(f, jar); // might close the stream, but that's ok
										// since it's the last entry
			}

			exitCode = createRunner().run("-application",
					"org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher", "-source", repoFeatures.toString(), //
					"-metadataRepository", repoOut.toUri().toURL().toExternalForm(), //
					"-artifactRepository", repoOut.toUri().toURL().toExternalForm(), //
					"-publishArtifacts", //
					// "-metadataRepositoryName", "foo1",
					// "-artifactRepositoryName", "bar",
					"-append");
			if (exitCode != 0)
				throw new MojoExecutionException("fab failed: exitCode=" + exitCode);

			Files.write(repoOut.resolve("p2.index"),
					"version = 1\rmetadata.repository.factory.order = content.xml,\\!\rartifact.repository.factory.order = artifacts.xml,\\!\r"
							.getBytes(StandardCharsets.US_ASCII));

			project.setContextValue("key", project.toString());
		} catch (MojoExecutionException e) {
			throw e;
		} catch (Exception e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}

	}

	private Artifact resolve(Artifact artifact) {
		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(artifact);
		request.setRemoteRepositories(remoteRepositories);
		request.setLocalRepository(localRepository);
		ArtifactResolutionResult resolution = repositorySystem.resolve(request);

		for (ArtifactResolutionException e : resolution.getErrorArtifactExceptions()) {
			System.err.println("ERR " + e);
		}

		switch (resolution.getArtifacts().size()) {
		case 0:
			return null;
		case 1:
			return Iterables.getOnlyElement(resolution.getArtifacts());
		default:
			throw new Error();
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
					throw new MojoExecutionException("FIXME");
				String sbundle = elements[0].getValue();
				String sversion = elements[0].getAttribute("version");
				if (!Objects.equals(plugin.id, sbundle))
					throw new MojoExecutionException("FIXME " + plugin.id + " " + sbundle);
				if (!Objects.equals(plugin.version, sversion))
					throw new MojoExecutionException("FIXME " + plugin.version + " " + sversion);

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

	private Plugin createPlugin(Artifact a, Path resolve) throws Exception {
		try (Builder builder = new Builder()) {
			builder.setTrace(true);

			Jar classesDirJar = new Jar(a.getFile());
			// classesDirJar.setManifest(new Manifest());

			builder.setProperty(Constants.BUNDLE_SYMBOLICNAME, a.getArtifactId());
			// builder.setProperty(Constants.BUNDLE_NAME, project.getName());

			Version version = MavenVersion.parseString(a.getVersion()).getOSGiVersion();
			builder.setProperty(Constants.BUNDLE_VERSION, version.toString());
			builder.setProperty(Constants.EXPORT_PACKAGE, "*");

			// builder.setProperty("ver", "1.1.1");
			// builder.setProperty("Export-Package", "*;version=${ver}");
			builder.setJar(classesDirJar);

			Jar j = builder.build();

			if (false)
				for (Object o : j.getManifest().getMainAttributes().entrySet())
					System.err.println(o);

			j.write(resolve.toFile());

			return scanPlugin(resolve);
		}
	}

	protected List<ArtifactRepository> getPluginRepositories(MavenSession session) {
		List<ArtifactRepository> repositories = new ArrayList<>();
		for (MavenProject project : session.getProjects()) {
			repositories.addAll(project.getPluginArtifactRepositories());
		}
		return repositorySystem.getEffectiveRepositories(repositories);
	}

	public Artifact resolveDependency(MavenSession session, Artifact artifact) throws MavenExecutionException {

		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(artifact);
		request.setResolveRoot(true).setResolveTransitively(false);
		request.setLocalRepository(session.getLocalRepository());
		request.setRemoteRepositories(getPluginRepositories(session));
		request.setCache(session.getRepositoryCache());
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

	EquinoxRunner runner;

	EquinoxRunner createRunner() throws IOException, MavenExecutionException {
		if (runner == null) {
			Artifact platform = resolveDependency(session,
					repositorySystem.createArtifact("org.eclipse.tycho", "tycho-bundles-external", "0.26.0", "zip"));

			Path p = installer.addRuntimeArtifact(session, platform);
			runner = runnerFactory.newBuilder().withInstallation(p).build();
		}
		return runner;
	}
}
