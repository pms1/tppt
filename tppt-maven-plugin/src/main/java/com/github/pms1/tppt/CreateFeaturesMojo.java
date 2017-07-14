package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.eclipse.osgi.service.resolver.VersionRange;
import org.osgi.framework.BundleException;

import com.github.pms1.tppt.jaxb.Feature;
import com.github.pms1.tppt.jaxb.Plugin;
import com.github.pms1.tppt.p2.ArtifactId;
import com.github.pms1.tppt.p2.DataCompression;
import com.github.pms1.tppt.p2.P2Repository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.P2RepositoryFactory.P2Kind;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataArtifact;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataProperties;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataProperty;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Provided;
import com.github.pms1.tppt.p2.jaxb.metadata.Provides;
import com.github.pms1.tppt.p2.jaxb.metadata.Required;
import com.github.pms1.tppt.p2.jaxb.metadata.Requires;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

import aQute.bnd.version.MavenVersion;
import aQute.bnd.version.Version;

/**
 * A maven mojo for creating a p2 repository from maven dependencies
 * 
 * @author pms1
 **/
@Mojo(name = "create-features", requiresDependencyResolution = ResolutionScope.COMPILE)
public class CreateFeaturesMojo extends AbstractMojo {

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

	@Parameter
	private String osgiVersion;

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

	@Component(hint = "xml")
	private DataCompression raw;

	private static Plugin scanPlugin(Path path, Plugin plugin)
			throws IOException, BundleException, MojoExecutionException {
		Preconditions.checkArgument(Files.isRegularFile(path), "Not a regular file: " + path);

		long uncompressedSize = 0;

		// Cannot use ZipInputStream since that leaves getCompressedSize() and
		// getSize() of ZipEntry unfilled
		if (!Files.isReadable(path))
			try (ZipFile zf = new ZipFile(path.toFile())) {
				for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
					ZipEntry entry = e.nextElement();

					if (entry.getSize() == -1)
						throw new Error();
					uncompressedSize += entry.getSize();
				}
			} catch (java.util.zip.ZipException e) {
				throw new RuntimeException("While opening " + path + ": " + e.getMessage());
			}

		plugin.download_size = Files.size(path) / 1024;
		plugin.install_size = uncompressedSize / 1024;

		return plugin;
	}

	static Version createOsgiVersion(String mavenVersion) {
		Version qualifiedVersion;

		Pattern pattern = Pattern.compile(
				"(?<m11>\\d+)[.](?<m12>\\d+)[.](?<m13>\\d+)([.].*|-SNAPSHOT|)$|(?<m21>\\d+)[.](?<m22>\\d+)([.].*|-SNAPSHOT|)$|(?<m31>\\d+)([.].*|-SNAPSHOT|)$");
		Matcher m = pattern.matcher(mavenVersion);
		if (m.matches())
			if (m.group("m11") != null)
				qualifiedVersion = new Version(Integer.parseInt(m.group("m11")), Integer.parseInt(m.group("m12")),
						Integer.parseInt(m.group("m13")));
			else if (m.group("m21") != null)
				qualifiedVersion = new Version(Integer.parseInt(m.group("m21")), Integer.parseInt(m.group("m22")), 0);
			else if (m.group("m31") != null)
				qualifiedVersion = new Version(Integer.parseInt(m.group("m31")), 0, 0);
			else
				throw new Error();
		else
			qualifiedVersion = new Version(1, 0, 0);

		return qualifiedVersion;
	}

	private static Predicate<Required> isFeatureRequirement = r -> Objects.equals(r.getNamespace(),
			"org.eclipse.equinox.p2.iu") && r.getName() != null && r.getName().endsWith(".feature.group");

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			if (project.getName() == null)
				throw new MojoExecutionException("Not supposed to happen: ${project.name} is null");

			Version unqualified;
			if (osgiVersion != null)
				unqualified = MavenVersion.parseMavenString(osgiVersion).getOSGiVersion();
			else
				unqualified = createOsgiVersion(project.getVersion());

			final String buildQualifier = project.getProperties().getProperty("buildQualifier");
			if (Strings.isNullOrEmpty(buildQualifier))
				throw new MojoExecutionException("Project does not have build qualifier set");

			Version qualifiedVersion = new Version(unqualified.getMajor(), unqualified.getMinor(),
					unqualified.getMicro(), buildQualifier);
			getLog().info("Feature version is " + qualifiedVersion);

			final Path repoFeatures = target.toPath().resolve("repository-features");
			final Path repoFeaturesFeatures = repoFeatures.resolve("features");
			final Path repoOut = target.toPath().resolve("repository");

			List<Plugin> plugins = new ArrayList<>();

			P2Repository p2 = p2repositoryFactory.loadContent(repoOut);
			if (p2 == null)
				throw new IllegalArgumentException("Could not find a p2 repository at " + repoOut);

			if (p2.getMetadataRepositoryFacade().getRepository().getUnits() == null)
				return;

			for (Unit u : p2.getMetadataRepositoryFacade().getRepository().getUnits().getUnit()) {
				Optional<Provided> provided = u.getProvides().getProvided().stream()
						.filter(p -> p.getNamespace().equals("osgi.bundle")).findAny();
				if (!provided.isPresent())
					continue;

				// filter requirements on feature iu's since eclipse does not
				// resolve them in the target editor
				if (u.getRequires() != null && u.getRequires().getRequired() != null)
					if (u.getRequires().getRequired().stream().anyMatch(isFeatureRequirement))
						continue;

				Optional<Provided> fragment = u.getProvides().getProvided().stream()
						.filter(p -> p.getNamespace().equals("osgi.fragment")).findAny();

				// if this unit has no artifacts, ignore it (#28)
				if (u.getArtifacts() == null)
					continue;

				if (u.getArtifacts().getArtifact().size() != 1)
					throw new Error("Unit with multiple artifacts: " + u.getId() + " " + u.getVersion());

				MetadataArtifact a = Iterables.getOnlyElement(u.getArtifacts().getArtifact());
				Path path = p2.getArtifactRepositoryFacade()
						.getArtifactUri(new ArtifactId(a.getId(), a.getVersion(), a.getClassifier()));

				Plugin p = new Plugin();
				p.id = provided.get().getName();
				p.version = provided.get().getVersion().toString();
				scanPlugin(path, p);
				if (fragment.isPresent())
					p.fragment = true;
				p.unpack = false;
				p.filter = u.getFilter();
				plugins.add(p);
			}

			if (plugins.isEmpty())
				return;

			// ** create and publish feature
			Feature f = new Feature();
			f.description = project.getDescription();
			f.label = project.getName();
			f.id = project.getGroupId() + "." + project.getArtifactId();
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

			int exitCode = createRunner().run("-application",
					"org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher", //
					"-source", repoFeatures.toString(), //
					"-metadataRepository", repoOut.toUri().toURL().toExternalForm(), //
					"-artifactRepository", repoOut.toUri().toURL().toExternalForm(), //
					"-publishArtifacts", //
					"-append");
			if (exitCode != 0)
				throw new MojoExecutionException("fab failed: exitCode=" + exitCode);

			final Path repoCategories = target.toPath().resolve("repository-categories");
			Files.createDirectory(repoCategories);

			P2Repository repository = p2repositoryFactory.createContent(repoCategories, P2Kind.metadata);

			MetadataRepository repo = repository.getMetadataRepositoryFacade().getRepository();
			repo.setName("Categories for " + project.getName());

			Unit u = new Unit();

			u.setId(project.getGroupId() + "." + project.getArtifactId() + ".categories.default");
			org.osgi.framework.Version osgiVersion = org.osgi.framework.Version
					.parseVersion(qualifiedVersion.toString());
			u.setVersion(osgiVersion);

			u.setProperties(new MetadataProperties());
			u.getProperties().getProperty().add(createProperty("org.eclipse.equinox.p2.name", project.getName()));
			u.getProperties().getProperty().add(createProperty("org.eclipse.equinox.p2.type.category", "true"));

			u.setProvides(new Provides());
			Provided provided = new Provided();
			provided.setNamespace("org.eclipse.equinox.p2.iu");
			provided.setName(u.getId());
			provided.setVersion(osgiVersion);
			u.getProvides().getProvided().add(provided);

			u.setRequires(new Requires());
			Required required = new Required();
			required.setNamespace("org.eclipse.equinox.p2.iu");
			required.setName(f.id + ".feature.group");
			required.setRange(new VersionRange(osgiVersion, true, osgiVersion, true));
			u.getRequires().getRequired().add(required);

			repo.getUnits().getUnit().add(u);
			repository.save(raw);

			exitCode = createRunner().run("-application",
					"org.eclipse.equinox.p2.metadata.repository.mirrorApplication", //
					"-source", repoCategories.toUri().toURL().toExternalForm(), //
					"-destination", repoOut.toUri().toURL().toExternalForm());
			if (exitCode != 0)
				throw new MojoExecutionException("fab failed: exitCode=" + exitCode);
		} catch (MojoExecutionException e) {
			throw e;
		} catch (Exception e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}

	}

	private MetadataProperty createProperty(String name, String value) {
		MetadataProperty p = new MetadataProperty();
		p.setName(name);
		p.setValue(value);
		return p;
	}

	private List<ArtifactRepository> getPluginRepositories(MavenSession session) {
		List<ArtifactRepository> repositories = new ArrayList<>();
		for (MavenProject project : session.getProjects()) {
			repositories.addAll(project.getPluginArtifactRepositories());
		}
		return repositorySystem.getEffectiveRepositories(repositories);
	}

	private Artifact resolveDependency(MavenSession session, Artifact artifact) throws MavenExecutionException {

		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(artifact);
		request.setResolveRoot(true).setResolveTransitively(false);
		request.setLocalRepository(session.getLocalRepository());
		request.setRemoteRepositories(getPluginRepositories(session));
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
					repositorySystem.createArtifact("org.eclipse.tycho", "tycho-bundles-external", "1.0.0", "zip"));

			Path p = installer.addRuntimeArtifact(session, platform);
			runner = runnerFactory.newBuilder().withInstallation(p).build();
		}
		return runner;
	}
}
