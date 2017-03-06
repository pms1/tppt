package com.github.pms1.tppt.p2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.pms1.tppt.p2.jaxb.composite.Child;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;

public class CompositeRepositoryTest {

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void create() throws Exception {
		P2RepositoryFactory factory = plexusContainer.lookup(P2RepositoryFactory.class);

		P2CompositeRepository composite = factory.createComposite(folder.getRoot().toPath());

		CompositeRepository repository = composite.getCompositeArtifactRepositoryFacade().getRepository();

		repository.setName("name1");
		Child c = new Child();
		c.setLocation("foo1");
		repository.getChildren().getChild().add(c);

		repository = composite.getCompositeMetadataRepositoryFacade().getRepository();

		repository.setName("name2");
		c = new Child();
		c.setLocation("foo2");
		repository.getChildren().getChild().add(c);

		composite.save();

		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeArtifacts.xml"))).isTrue();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeContent.xml"))).isTrue();

		P2CompositeRepository loaded = factory.loadComposite(folder.getRoot().toPath());

		CompositeRepository repoloaded = loaded.getCompositeArtifactRepositoryFacade().getRepository();

		assertThat(repoloaded.getName()).isEqualTo("name1");

		assertThat(repoloaded.getChildren().getChild()).extracting(Child::getLocation).containsExactly("foo1");

		repoloaded = loaded.getCompositeMetadataRepositoryFacade().getRepository();

		assertThat(repoloaded.getName()).isEqualTo("name2");

		assertThat(repoloaded.getChildren().getChild()).extracting(Child::getLocation).containsExactly("foo2");

		composite.setCompression(plexusContainer.lookup(DataCompression.class, "jar"));

		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeArtifacts.xml"))).isFalse();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeContent.xml"))).isFalse();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeArtifacts.jar"))).isTrue();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeContent.jar"))).isTrue();
	}
}
