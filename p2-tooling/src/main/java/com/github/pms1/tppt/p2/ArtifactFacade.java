package com.github.pms1.tppt.p2;

import com.github.pms1.tppt.p2.jaxb.artifact.Artifact;

public interface ArtifactFacade {
	ArtifactId getId();

	String getClassifier();

	Artifact getData();
}
