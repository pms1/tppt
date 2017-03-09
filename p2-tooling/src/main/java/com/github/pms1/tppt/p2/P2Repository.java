package com.github.pms1.tppt.p2;

import java.io.IOException;

public interface P2Repository extends CommonP2Repository {
	ArtifactRepositoryFacade getArtifactRepositoryFacade() throws IOException;

	MetadataRepositoryFacade getMetadataRepositoryFacade() throws IOException;
}
