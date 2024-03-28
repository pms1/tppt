package com.github.pms1.tppt;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

import com.github.pms1.tppt.core.DeploymentHelper;
import com.github.pms1.tppt.p2.CompositeRepositoryFacade;
import com.github.pms1.tppt.p2.DataCompression;
import com.github.pms1.tppt.p2.P2CompositeRepository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.jaxb.composite.Child;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;

/**
 * A maven mojo for creating a p2 composite repository from maven dependencies
 * 
 * @author pms1
 **/
@Mojo(name = "create-composite-repository", requiresDependencyResolution = ResolutionScope.COMPILE)
public class CreateCompositeRepository extends AbstractMojo {

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
	private ResolutionErrorHandler resolutionErrorHandler;

	@Component(hint = "default")
	private DependencyGraphBuilder dependencyGraphBuilder;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

	@Component
	private DeploymentHelper deployHelp;

	@Component
	private P2RepositoryFactory factory;

	@Component(hint = "xml")
	private DataCompression raw;

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

	public void execute() throws MojoExecutionException, MojoFailureException {

		try {

			ProjectBuildingRequest pbRequest = new DefaultProjectBuildingRequest();
			pbRequest.setLocalRepository(localRepository);
			pbRequest.setProject(project);
			pbRequest.setRemoteRepositories(remoteRepositories);
			pbRequest.setRepositorySession(session.getRepositorySession());
			pbRequest.setResolveDependencies(true);

			DependencyNode n = dependencyGraphBuilder.buildDependencyGraph(pbRequest, null);

			Map<File, Boolean> dependentFiles = new HashMap<>();

			n.accept(new DependencyNodeVisitor() {
				@Override
				public boolean visit(DependencyNode node) {
					// ourselfves
					if (node.getParent() == null)
						return true;

					Artifact a;
					try {
						a = resolveDependency(node.getArtifact());
					} catch (MavenExecutionException e) {
						throw new RuntimeException(e);
					}

					if (a.getFile() == null)
						throw new Error();

					dependentFiles.put(a.getFile(), node.getParent().getParent() == null);

					return true;
				}

				@Override
				public boolean endVisit(DependencyNode node) {
					return true;
				}
			});

			final Path repoOut = target.toPath().resolve("repository");
			Files.createDirectories(repoOut);

			P2CompositeRepository composite = factory.createComposite(repoOut);

			CompositeRepositoryFacade artifactRepositoryFacade = composite.getArtifactRepositoryFacade();
			CompositeRepositoryFacade metadataRepositoryFacade = composite.getMetadataRepositoryFacade();
			CompositeRepository artifactRepository = artifactRepositoryFacade.getRepository();
			CompositeRepository metadataRepository = metadataRepositoryFacade.getRepository();

			artifactRepository.setName(project.getName());
			metadataRepository.setName(project.getName());

			Path localPath = Paths.get(deployHelp.getPath(project, LocalDateTime.now()));

			for (MavenProject p : session.getProjects()) {

				Boolean b = dependentFiles.get(p.getArtifact().getFile());
				if (b == null)
					continue;

				switch (p.getPackaging()) {
				case "tppt-repository":
				case "tppt-composite-repository":
					break;
				default:
					if (b)
						throw new MojoExecutionException(
								"Direct dependencies must have packaging 'tppt-repository' or 'tppt-composite-repository': "
										+ p.getArtifact());
					else
						continue;
				}

				String rel = relativize(localPath, Paths.get(deployHelp.getPath(p)));

				Child c = new Child();
				c.setLocation(rel);
				artifactRepository.getChildren().getChild().add(c);
				c = new Child();
				c.setLocation(rel);
				metadataRepository.getChildren().getChild().add(c);

			}

			LocalDateTime now = LocalDateTime.now();
			long ts = now.toEpochSecond(ZoneOffset.UTC) * 1000 + now.getNano() / 1_000_000;

			artifactRepositoryFacade.setTimestamp(ts);
			metadataRepositoryFacade.setTimestamp(ts);

			composite.save(raw);
		} catch (MojoExecutionException e) {
			throw e;
		} catch (Exception e) {
			throw new MojoExecutionException("mojo failed: " + e.getMessage(), e);
		}
	}

	private String relativize(Path p1, Path p2) {
		return StreamSupport.stream(p1.relativize(p2).spliterator(), false) //
				.map(Object::toString) //
				.collect(Collectors.joining("/"));
	}

}
