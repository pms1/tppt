package com.github.pms1.tppt.p2;

import java.nio.file.Path;
import java.util.Map;

import com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository;

public interface ArtifactRepositoryFacade extends RepositoryFacade<ArtifactRepository> {

	Map<ArtifactId, ArtifactFacade> getArtifacts();

	Path getArtifactUri(ArtifactId id);
}
