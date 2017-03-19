package com.github.pms1.tppt.p2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.osgi.framework.Version;

import com.github.pms1.tppt.p2.P2RepositoryFactory.P2Kind;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;

public class ContentRepositoryTest {

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void create() throws Exception {
		P2RepositoryFactory factory = plexusContainer.lookup(P2RepositoryFactory.class);

		P2Repository composite = factory.createContent(folder.getRoot().toPath(), P2Kind.metadata);

		MetadataRepository metadataRepository = composite.getMetadataRepositoryFacade().getRepository();

		metadataRepository.setName("name2");
		Unit u = new Unit();
		u.setId("u1");
		u.setVersion(Version.parseVersion("2.0.0"));
		metadataRepository.getUnits().getUnit().add(u);

		composite.save(plexusContainer.lookup(DataCompression.class, "xml"));

		assertThat(Files.exists(folder.getRoot().toPath().resolve("artifacts.xml"))).isFalse();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("content.xml"))).isTrue();

		P2Repository loaded = factory.loadContent(folder.getRoot().toPath(), P2Kind.metadata);

		metadataRepository = loaded.getMetadataRepositoryFacade().getRepository();

		assertThat(metadataRepository.getName()).isEqualTo("name2");

		assertThat(metadataRepository.getUnits().getSize()).isEqualTo(1);
		assertThat(metadataRepository.getUnits().getUnit()).extracting(Unit::getId).containsExactly("u1");

		composite.setCompression(plexusContainer.lookup(DataCompression.class, "jar"));

		assertThat(Files.exists(folder.getRoot().toPath().resolve("artifacts.xml"))).isFalse();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("content.xml"))).isFalse();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("artifacts.jar"))).isFalse();
		assertThat(Files.exists(folder.getRoot().toPath().resolve("content.jar"))).isTrue();
	}

}
