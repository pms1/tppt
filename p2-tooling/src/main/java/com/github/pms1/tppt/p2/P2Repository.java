package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface P2Repository {
	ArtifactRepositoryFacade getArtifactRepositoryFacade() throws IOException;

	MetadataRepositoryFacade getMetadataRepositoryFacade() throws IOException;

	Path getPath();

	Set<DataCompression> getMetadataDataCompressions();

	Set<DataCompression> getArtifactDataCompressions();
}
