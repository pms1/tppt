package com.github.pms1.tppt.p2;

import java.net.URI;
import java.util.Map;

public interface ArtifactRepositoryFacade {

	Map<ArtifactId, Artifact> getArtifacts();

	URI getArtifactUri(ArtifactId id);
}
