package com.github.pms1.tppt;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.maven.artifact.repository.ArtifactRepository;
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

import com.github.pms1.tppt.core.DeploymentHelper;
import com.github.pms1.tppt.p2.P2CompositeRepository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.jaxb.composite.Child;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.composite.Property;

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
	private DeploymentHelper deployHelp;

	@Component
	private P2RepositoryFactory factory;

	@Parameter
	private ArtifactFilter exclusions = new ExclusionSetFilter(Collections.emptySet());

	public void setExclusions(String[] exclusions) {
		this.exclusions = new ExclusionSetFilter(exclusions);
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

		try {

			ProjectBuildingRequest pbRequest = new DefaultProjectBuildingRequest();
			pbRequest.setLocalRepository(localRepository);
			pbRequest.setProject(project);
			pbRequest.setRemoteRepositories(remoteRepositories);
			pbRequest.setRepositorySession(session.getRepositorySession());
			pbRequest.setResolveDependencies(true);
			pbRequest.setResolveVersionRanges(true);

			DependencyNode n = dependencyGraphBuilder.buildDependencyGraph(pbRequest, null);

			Set<File> dependentFiles = new HashSet<>();

			n.accept(new DependencyNodeVisitor() {
				@Override
				public boolean visit(DependencyNode node) {
					if (node.getParent() == null)
						return true;

					if (node.getArtifact().getFile() == null)
						throw new Error();

					if (!dependentFiles.add(node.getArtifact().getFile()))
						throw new Error();

					return false;
				}

				@Override
				public boolean endVisit(DependencyNode node) {
					return true;
				}
			});

			final Path repoOut = target.toPath().resolve("repository");
			Files.createDirectories(repoOut);

			P2CompositeRepository composite = factory.createComposite(repoOut);

			CompositeRepository artifactRepository = composite.getArtifactRepositoryFacade().getRepository();
			CompositeRepository metadataRepository = composite.getMetadataRepositoryFacade().getRepository();

			artifactRepository.setName("name1");
			metadataRepository.setName("name2");

			Path localPath = deployHelp.getPath(project, LocalDateTime.now());

			for (MavenProject p : session.getProjects()) {
				if (!dependentFiles.contains(p.getArtifact().getFile()))
					continue;

				String rel = relativize(localPath, deployHelp.getPath(p));

				Child c = new Child();
				c.setLocation(rel);
				artifactRepository.getChildren().getChild().add(c);
				c = new Child();
				c.setLocation(rel);
				metadataRepository.getChildren().getChild().add(c);

			}

			LocalDateTime now = LocalDateTime.now();
			long ts = now.toEpochSecond(ZoneOffset.UTC) * 1000 + now.getNano() / 1_000_000;

			Property p = new Property();
			p.setName("p2.timestamp");
			p.setValue(Long.toString(ts));
			artifactRepository.getProperties().getProperty().add(p);
			p = new Property();
			p.setName("p2.timestamp");
			p.setValue(Long.toString(ts));
			metadataRepository.getProperties().getProperty().add(p);

			composite.save();
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
