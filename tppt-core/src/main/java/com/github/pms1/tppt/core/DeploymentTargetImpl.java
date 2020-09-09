package com.github.pms1.tppt.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.pms1.tppt.p2.DataCompression;
import com.github.pms1.tppt.p2.P2CompositeRepository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.P2RepositoryFactory.NoRepositoryFoundException;
import com.github.pms1.tppt.p2.jaxb.composite.Child;

class DeploymentTargetImpl implements DeploymentTarget {

	private final Path root;
	private final P2RepositoryFactory factory;
	private final DataCompression raw;

	public DeploymentTargetImpl(Path path, P2RepositoryFactory factory, DataCompression raw) {
		this.root = path;
		this.factory = factory;
		this.raw = raw;
	}

	private P2CompositeRepository repo;

	public Collection<Path> findRepositories() throws IOException {
		loadRepo();

		Set<String> a = repo.getArtifactRepositoryFacade().getRepository().getChildren().getChild().stream()
				.map(Child::getLocation).collect(Collectors.toSet());
		Set<String> m = repo.getMetadataRepositoryFacade().getRepository().getChildren().getChild().stream()
				.map(Child::getLocation).collect(Collectors.toSet());
		if (!Objects.equals(a, m))
			throw new Error();

		return a.stream().map(root.getFileSystem()::getPath).collect(Collectors.toSet());
	}

	@Override
	public void close() {
	}

	private DataCompression[] repoCompressions;

	private void loadRepo() throws IOException {
		if (repo != null)
			return;

		try {
			repo = factory.loadComposite(root);
			repoCompressions = new DataCompression[0];
		} catch (NoRepositoryFoundException e) {

			P2CompositeRepository r = factory.createComposite(root);
			r.getArtifactRepositoryFacade().getRepository().setName("Repository Index");
			r.getMetadataRepositoryFacade().getRepository().setName("Repository Index");

			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (Files.exists(dir.resolve("p2.index"))) {
						addRepo(r, root.relativize(dir));
					}

					return super.preVisitDirectory(dir, attrs);
				}

			});

			repo = r;
			repoCompressions = new DataCompression[] { raw };
		}
	}

	private void addRepo(P2CompositeRepository r, Path rel) throws IOException {
		Child c = new Child();
		c.setLocation(rel.toString());
		r.getArtifactRepositoryFacade().getRepository().getChildren().getChild().add(c);
		c = new Child();
		c.setLocation(rel.toString());
		r.getMetadataRepositoryFacade().getRepository().getChildren().getChild().add(c);
	}

	@Override
	public Path getPath() {
		return root;
	}

	@Override
	public void addRepository(Path targetRoot) throws IOException {
		loadRepo();

		addRepo(repo, targetRoot);
	}

	@Override
	public void writeIndex() throws IOException {
		loadRepo();

		repo.save(repoCompressions);
		repoCompressions = new DataCompression[0];
	}

}
