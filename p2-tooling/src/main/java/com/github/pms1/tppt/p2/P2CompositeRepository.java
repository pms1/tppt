package com.github.pms1.tppt.p2;

import java.io.IOException;

public interface P2CompositeRepository extends CommonP2Repository {
	CompositeRepositoryFacade getCompositeArtifactRepositoryFacade() throws IOException;

	CompositeRepositoryFacade getCompositeMetadataRepositoryFacade() throws IOException;

	void save() throws IOException;

}
