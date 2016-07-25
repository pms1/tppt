package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

import com.github.pms1.tppt.jaxb.Feature;
import com.github.pms1.tppt.jaxb.Plugin;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;

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
	private List<ArtifactRepository> remoteArtifactRepositories;

	@Parameter(readonly = true, required = true, defaultValue = "${localRepository}")
	private ArtifactRepository localRepository;

	@Component
	private EquinoxRunnerFactory runnerFactory;

	@Component
	private TychoArtifactUnpacker installer;

	@Component
	private ResolutionErrorHandler resolutionErrorHandler;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

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
			throw new IllegalStateException();

		ManifestElement[] elements = ManifestElement.parseHeader(Constants.BUNDLE_SYMBOLICNAME,
				manifest.get(Constants.BUNDLE_SYMBOLICNAME));
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

			for (Artifact a : project.getArtifacts()) {
				Plugin plugin = scanPlugin(a.getFile().toPath());
				if (plugin == null) {
					throw new Error();
				}
				plugins.add(plugin);

				Files.copy(a.getFile().toPath(), repoDependenciesPlugins.resolve(a.getFile().toPath().getFileName()));

				// try to find artifacts with "sources" qualifier
				Artifact sourcesArtifact = repositorySystem.createArtifactWithClassifier(a.getGroupId(),
						a.getArtifactId(), a.getVersion(), "jar", "sources");
				ArtifactResolutionRequest request = new ArtifactResolutionRequest();
				request.setArtifact(sourcesArtifact);
				request.setRemoteRepositories(remoteArtifactRepositories);
				request.setLocalRepository(localRepository);
				ArtifactResolutionResult resolution = repositorySystem.resolve(request);

				switch (resolution.getArtifacts().size()) {
				case 0:
					// no sources: sad, but ok
					break;
				case 1:
					Artifact sourceArtifact = Iterables.getOnlyElement(resolution.getArtifacts());

					Manifest mf = null;

					try (ZipFile zf = new ZipFile(sourceArtifact.getFile())) {
						ZipEntry e = zf.getEntry("META-INF/MANIFEST.MF");
						if (e != null)
							mf = new Manifest(zf.getInputStream(e));
					}

					boolean sourceHasHeaders = false;

					if (mf != null) {
						String sourceBundle = mf.getMainAttributes().getValue("Eclipse-SourceBundle");

						if (sourceBundle != null) {
							ManifestElement[] elements = ManifestElement.parseHeader("Eclipse-SourceBundle",
									sourceBundle);
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

					Path out1 = repoDependenciesPlugins.resolve(sourceArtifact.getFile().toPath().getFileName());
					if (sourceHasHeaders) {
						Files.copy(sourceArtifact.getFile().toPath(), out1);
					} else {
						if (mf == null)
							mf = new Manifest();
						mf.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
						mf.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, plugin.id + ".source");
						mf.getMainAttributes().putValue(Constants.BUNDLE_VERSION, plugin.version);
						mf.getMainAttributes().putValue("Eclipse-SourceBundle",
								plugin.id + ";version=\"" + plugin.version + "\"");

						try (JarFile zf = new JarFile(sourceArtifact.getFile());
								OutputStream os = Files.newOutputStream(out1);
								JarOutputStream jar = new JarOutputStream(os, mf)) {

							for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
								ZipEntry e1 = e.nextElement();
								if (e1.getName().equals("META-INF/MANIFEST.MF"))
									continue;

								jar.putNextEntry(new JarEntry(e1));

								ByteStreams.copy(zf.getInputStream(e1), jar);
							}
						}
					}

					plugins.add(scanPlugin(out1));

					break;
				default:
					for (Artifact a1 : resolution.getArtifacts()) {
						System.err.println("SOURCE " + a1 + " " + a1.isResolved() + " " + a1.getFile());
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
		} catch (IOException | BundleException | InterruptedException | MavenExecutionException e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
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
					repositorySystem.createArtifact("org.eclipse.tycho", "tycho-bundles-external", "0.25.0", "zip"));

			Path p = installer.addRuntimeArtifact(session, platform);
			runner = runnerFactory.newBuilder().withInstallation(p).build();
		}
		return runner;
	}
}
