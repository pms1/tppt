package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
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
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Properties;
import com.github.pms1.tppt.p2.jaxb.metadata.Property;
import com.github.pms1.tppt.p2.jaxb.metadata.Provided;
import com.github.pms1.tppt.p2.jaxb.metadata.Provides;
import com.github.pms1.tppt.p2.jaxb.metadata.Required;
import com.github.pms1.tppt.p2.jaxb.metadata.Requires;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;
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

	private static Plugin scanPlugin(Path path, Plugin plugin)
			throws IOException, BundleException, MojoExecutionException {
		long uncompressedSize = 0;

		// Cannot use ZipInputStream since that leaves getCompressedSize() and
		// getSize() of ZipEntry unfilled
		try (ZipFile zf = new ZipFile(path.toFile())) {
			for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
				ZipEntry entry = e.nextElement();

				if (entry.getSize() == -1)
					throw new Error();
				uncompressedSize += entry.getSize();
			}
		}

		plugin.download_size = Files.size(path) / 1024;
		plugin.install_size = uncompressedSize / 1024;

		return plugin;
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			if (project.getName() == null)
				throw new MojoExecutionException("Not supposed to happen: ${project.name} is null");

			final String buildQualifier = project.getProperties().getProperty("buildQualifier");
			if (Strings.isNullOrEmpty(buildQualifier))
				throw new MojoExecutionException("Project does not have build qualifier set");

			Version unqualifiedVersion = MavenVersion.parseString(project.getVersion()).getOSGiVersion();

			Version qualifiedVersion = new Version(unqualifiedVersion.getMajor(), unqualifiedVersion.getMinor(),
					unqualifiedVersion.getMinor(), buildQualifier);

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

				Optional<Provided> fragment = u.getProvides().getProvided().stream()
						.filter(p -> p.getNamespace().equals("osgi.fragment")).findAny();

				if (u.getArtifacts().getArtifact().size() != 1)
					throw new Error();

				MetadataArtifact a = Iterables.getOnlyElement(u.getArtifacts().getArtifact());
				Path path = p2.getArtifactRepositoryFacade().getArtifactUri(new ArtifactId(a.getId(), a.getVersion()));

				Plugin p = new Plugin();
				p.id = provided.get().getName();
				p.version = provided.get().getVersion().toString();
				scanPlugin(path, p);
				if (fragment.isPresent())
					p.fragment = true;
				p.unpack = false;
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

			Unit u = new Unit();

			u.setId(project.getGroupId() + "." + project.getArtifactId() + ".categories.default");
			org.osgi.framework.Version osgiVersion = org.osgi.framework.Version
					.parseVersion(qualifiedVersion.toString());
			u.setVersion(osgiVersion);

			u.setProperties(new Properties());
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

	private Property createProperty(String name, String value) {
		Property p = new Property();
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
