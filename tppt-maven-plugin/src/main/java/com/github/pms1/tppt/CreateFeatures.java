package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

import com.github.pms1.tppt.jaxb.Feature;
import com.github.pms1.tppt.jaxb.Plugin;
import com.github.pms1.tppt.p2.ArtifactId;
import com.github.pms1.tppt.p2.P2Repository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataArtifact;
import com.github.pms1.tppt.p2.jaxb.metadata.Provided;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;

import aQute.bnd.version.MavenVersion;
import aQute.bnd.version.Version;

/**
 * A maven mojo for creating a p2 repository from maven dependencies
 * 
 * @author pms1
 **/
@Mojo(name = "create-features", requiresDependencyResolution = ResolutionScope.COMPILE)
public class CreateFeatures extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
	private File target;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${mojoExecution}", readonly = true)
	private MojoExecution mojoExecution;

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

	@Component
	private P2RepositoryFactory p2repositoryFactory;

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

	static Plugin scanPlugin(Path path, Plugin plugin) throws IOException, BundleException, MojoExecutionException {
		long compressedSize = 0;
		long uncompressedSize = 0;

		// Cannot use ZipInputStream since that leaves getCompressedSize() and
		// getSize() of ZipEntry unfilled
		try (ZipFile zf = new ZipFile(path.toFile())) {
			for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
				ZipEntry entry = e.nextElement();

				if (entry.getCompressedSize() == -1)
					throw new Error();
				compressedSize += entry.getCompressedSize();

				if (entry.getSize() == -1)
					throw new Error();
				uncompressedSize += entry.getSize();
			}
		}

		plugin.download_size = compressedSize / 1024;
		plugin.install_size = uncompressedSize / 1024;

		return plugin;
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

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			final String buildQualifier = project.getProperties().getProperty("buildQualifier");
			if (Strings.isNullOrEmpty(buildQualifier))
				throw new MojoExecutionException("FIXME");

			Version unqualifiedVersion = MavenVersion.parseString(project.getVersion()).getOSGiVersion();

			Version qualifiedVersion = new Version(unqualifiedVersion.getMajor(), unqualifiedVersion.getMinor(),
					unqualifiedVersion.getMinor(), buildQualifier);

			final Path repoFeatures = target.toPath().resolve("repository-features");
			final Path repoFeaturesFeatures = repoFeatures.resolve("features");
			final Path repoOut = target.toPath().resolve("repository");

			List<Plugin> plugins = new ArrayList<>();

			P2Repository p2 = p2repositoryFactory.create(repoOut);
			for (Unit u : p2.getMetadataRepositoryFacade().getMetadata().getUnits().getUnit()) {
				Optional<Provided> provided = u.getProvides().getProvided().stream()
						.filter(p -> p.getNamespace().equals("osgi.bundle")).findAny();
				if (!provided.isPresent())
					continue;

				if (u.getArtifacts().getArtifact().size() != 1)
					throw new Error();

				MetadataArtifact a = Iterables.getOnlyElement(u.getArtifacts().getArtifact());
				Path path = p2.getArtifactRepositoryFacade().getArtifactUri(new ArtifactId(a.getId(), a.getVersion()));
				System.err.println("P " + path);

				Plugin p = new Plugin();
				p.id = provided.get().getName();
				p.version = provided.get().getVersion().toString();
				scanPlugin(path, p);
				p.unpack = true; // FIXME
				plugins.add(p);
			}

			// ** create and publish feature
			Feature f = new Feature();
			f.description = project.getDescription();
			f.label = project.getName(); // null is ok
			f.id = project.getArtifactId();
			f.version = qualifiedVersion.toString();
			f.plugins = plugins.toArray(new Plugin[plugins.size()]);

			Files.createDirectories(repoFeaturesFeatures);

			try (OutputStream os = Files.newOutputStream(repoFeaturesFeatures.resolve("feature.jar"));
					JarOutputStream jar = new JarOutputStream(os)) {
				JarEntry je = new JarEntry("feature.xml");

				jar.putNextEntry(je);
				JAXB.marshal(f, jar); // might close the stream, but that's ok
										// since it's the last entry
			}

			StringWriter sw = new StringWriter();
			JAXB.marshal(f, sw);

			int exitCode = createRunner().run("-application", "tppt-mirror-application.id1", sw.toString());
			if (exitCode != 0)
				throw new MojoExecutionException("fab failed: exitCode=" + exitCode);

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

			Artifact extra = resolveDependency(session, repositorySystem.createArtifact("com.github.pms1.tppt",
					"tppt-mirror-application", mojoExecution.getVersion(), "jar"));

			Path p = installer.addRuntimeArtifact(session, platform);
			runner = runnerFactory.newBuilder().withInstallation(p).withPlugin(extra.getFile().toPath()).build();
		}
		return runner;
	}
}
