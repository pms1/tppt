package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;

import com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;

class P2RepositoryImpl implements P2Repository {

	private final Path path;
	private final Supplier<ArtifactRepository> artifactRepositorySupplier;
	private final Set<DataCompression> availableArtifacts;
	private final DataCompression preferedArtifacts;
	private final Supplier<MetadataRepository> metadataRepositorySupplier;
	private final Set<DataCompression> availableMetadata;
	private final DataCompression preferedMetadata;

	public P2RepositoryImpl(Path path, Supplier<ArtifactRepository> artifactRepositorySupplier,
			Set<DataCompression> availableArtifacts, DataCompression preferedArtifacts,
			Supplier<MetadataRepository> metadataRepositoryProducer, Set<DataCompression> availableMetadata,
			DataCompression preferedMetadata) {
		this.path = path;
		this.artifactRepositorySupplier = artifactRepositorySupplier;
		this.availableArtifacts = availableArtifacts;
		this.preferedArtifacts = preferedArtifacts;
		this.metadataRepositorySupplier = metadataRepositoryProducer;
		this.availableMetadata = availableMetadata;
		this.preferedMetadata = preferedMetadata;
	}

	@Override
	public MetadataRepository getMetadataRepository() {
		return metadataRepositorySupplier.get();
	}

	@Override
	public ArtifactRepositoryFacade getArtifactRepositoryFacade() throws IOException {
		return new ArtifactRepositoryFactoryImpl(path.toUri(), artifactRepositorySupplier.get());
	}

	@Override
	public Path getPath() {
		return path;
	}
}
