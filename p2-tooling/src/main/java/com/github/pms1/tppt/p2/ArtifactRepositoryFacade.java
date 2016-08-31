package com.github.pms1.tppt.p2;

import java.nio.file.Path;
import java.util.Map;

public interface ArtifactRepositoryFacade extends RepositoryFacade {

	Map<ArtifactId, ArtifactFacade> getArtifacts();

	Path getArtifactUri(ArtifactId id);
}
