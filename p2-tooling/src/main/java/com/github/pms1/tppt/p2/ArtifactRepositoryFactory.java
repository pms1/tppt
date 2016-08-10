package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = ArtifactRepositoryFactory.class)
public class ArtifactRepositoryFactory
		extends AbstractRepositoryFactory<com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository> {

	protected ArtifactRepositoryFactory() {
		super(com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository.class, "artifact", "artifacts",
				"artifactRepository.xsd");
	}

	public ArtifactRepository read(Path p) throws IOException {
		return new ArtifactRepositoryImpl(p.toUri(), readRepository(p));
	}

	public ArtifactRepository read(InputStream is) {
		return new ArtifactRepositoryImpl(null, readStream(is));
	}
}
