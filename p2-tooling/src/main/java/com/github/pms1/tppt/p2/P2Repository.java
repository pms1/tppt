package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;

public interface P2Repository {

	MetadataRepository getMetadataRepository() throws IOException;

	ArtifactRepositoryFacade getArtifactRepositoryFacade() throws IOException;

	Path getPath();

}
