package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;

import org.codehaus.plexus.component.annotations.Component;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;

@Component(role = MetadataRepositoryFactory.class)
public class MetadataRepositoryFactory extends AbstractRepositoryFactory<MetadataRepository> {

	protected MetadataRepositoryFactory() {
		super(MetadataRepository.class, "metadata", "content", "metadataRepository.xsd");
	}

	public MetadataRepositoryFacade createFacade(Path p) throws IOException {
		return new MetadataRepositoryFacadeImpl(p, readRepository(p));
	}
}
