package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface CommonP2Repository {

	void setCompression(DataCompression... compressions) throws IOException;

	void accept(P2RepositoryVisitor visitor);

	Set<DataCompression> getMetadataDataCompressions();

	Set<DataCompression> getArtifactDataCompressions();

	RepositoryFacade getArtifactRepositoryFacade() throws IOException;

	RepositoryFacade getMetadataRepositoryFacade() throws IOException;

	Path getPath();
}
