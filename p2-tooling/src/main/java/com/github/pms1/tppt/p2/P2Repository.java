package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;

public interface P2Repository {

	MetadataRepository getMetadataRepository() throws IOException;

	ArtifactRepositoryFacade getArtifactRepositoryFacade() throws IOException;

	Path getPath();

	Set<DataCompression> getMetadataDataCompressions();

	Set<DataCompression> getArtifactDataCompressions();
}
