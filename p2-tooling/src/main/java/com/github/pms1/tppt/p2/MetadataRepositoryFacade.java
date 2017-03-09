package com.github.pms1.tppt.p2;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;

public interface MetadataRepositoryFacade extends RepositoryFacade {

	MetadataRepository getRepository();
}
