package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface CommonP2Repository {

	void setCompression(DataCompression... compressions) throws IOException;

	<T> T accept(P2RepositoryVisitor<T> visitor);

	List<DataCompression> getMetadataDataCompressions();

	List<DataCompression> getArtifactDataCompressions();

	RepositoryFacade<?> getArtifactRepositoryFacade() throws IOException;

	RepositoryFacade<?> getMetadataRepositoryFacade() throws IOException;

	Path getPath();

	void save(DataCompression... compressions) throws IOException;

}
