package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = MetadataRepositoryFactory.class)
public class MetadataRepositoryFactory
		extends AbstractRepositoryFactory<com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository> {

	protected MetadataRepositoryFactory() {
		super(com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository.class, "metadata", "content",
				"metadataRepository.xsd");
	}

	public MetadataRepository read(Path p) throws IOException {

		return new MetadataRepositoryImpl(p.toUri(), readRepository(p));
	}
}
