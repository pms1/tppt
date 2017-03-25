package com.github.pms1.tppt.p2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.pms1.tppt.p2.jaxb.composite.Child;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeProperty;

public class CompositeRepositoryTest {

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void compare() throws Exception {
		P2RepositoryFactory factory = plexusContainer.lookup(P2RepositoryFactory.class);

		P2CompositeRepository composite1 = factory.createComposite(folder.getRoot().toPath());
		CompositeProperty p = new CompositeProperty();
		p.setName("n");
		p.setValue("v");
		composite1.getArtifactRepositoryFacade().getRepository().getProperties().getProperty().add(p);
		composite1.getArtifactRepositoryFacade().getRepository().getProperties().setSize(1);

		Child c = new Child();
		c.setLocation("foo1");
		composite1.getArtifactRepositoryFacade().getRepository().getChildren().getChild().add(c);
		composite1.getArtifactRepositoryFacade().getRepository().getChildren().setSize(1);

		P2CompositeRepository composite2 = factory.createComposite(folder.getRoot().toPath());
		p = new CompositeProperty();
		p.setName("n");
		p.setValue("v2");
		composite2.getArtifactRepositoryFacade().getRepository().getProperties().getProperty().add(p);
		composite2.getArtifactRepositoryFacade().getRepository().getProperties().setSize(2);
		c = new Child();
		c.setLocation("foo1");
		composite2.getArtifactRepositoryFacade().getRepository().getChildren().getChild().add(c);
		c = new Child();
		c.setLocation("foo2");
		composite2.getArtifactRepositoryFacade().getRepository().getChildren().getChild().add(c);
		composite2.getArtifactRepositoryFacade().getRepository().getChildren().setSize(2);

		RepositoryComparator comparator = plexusContainer.lookup(RepositoryComparator.class);

		comparator.run(composite1, composite2);

	}

	@Test
	public void create() throws Exception {
		P2RepositoryFactory factory = plexusContainer.lookup(P2RepositoryFactory.class);

		P2CompositeRepository composite = factory.createComposite(folder.getRoot().toPath());

		CompositeRepository repository = composite.getArtifactRepositoryFacade().getRepository();

		repository.setName("name1");
		Child c = new Child();
		c.setLocation("foo1");
		repository.getChildren().getChild().add(c);

		repository = composite.getMetadataRepositoryFacade().getRepository();

		repository.setName("name2");
		c = new Child();
		c.setLocation("foo2");
		repository.getChildren().getChild().add(c);

		composite.save(plexusContainer.lookup(DataCompression.class, "xml"));

		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeArtifacts.xml"))).isTrue();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeContent.xml"))).isTrue();

		P2CompositeRepository loaded = factory.loadComposite(folder.getRoot().toPath());

		CompositeRepository repoloaded = loaded.getArtifactRepositoryFacade().getRepository();

		assertThat(repoloaded.getName()).isEqualTo("name1");

		assertThat(repoloaded.getChildren().getSize()).isEqualTo(1);
		assertThat(repoloaded.getChildren().getChild()).extracting(Child::getLocation).containsExactly("foo1");

		repoloaded = loaded.getMetadataRepositoryFacade().getRepository();

		assertThat(repoloaded.getName()).isEqualTo("name2");

		assertThat(repoloaded.getChildren().getSize()).isEqualTo(1);
		assertThat(repoloaded.getChildren().getChild()).extracting(Child::getLocation).containsExactly("foo2");

		composite.setCompression(plexusContainer.lookup(DataCompression.class, "jar"));

		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeArtifacts.xml"))).isFalse();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeContent.xml"))).isFalse();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeArtifacts.jar"))).isTrue();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("compositeContent.jar"))).isTrue();
	}

}
