package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

class P2RepositoryImpl implements P2Repository {

	private final Path path;
	private final Supplier<ArtifactRepositoryFacade> artifactRepositoryFacadeSupplier;
	private final Set<DataCompression> availableArtifacts;
	private final DataCompression preferedArtifacts;
	private final Supplier<MetadataRepositoryFacade> metadataRepositoryFacadeSupplier;
	private final Set<DataCompression> availableMetadata;
	private final DataCompression preferedMetadata;

	public P2RepositoryImpl(Path path, Supplier<ArtifactRepositoryFacade> artifactRepositorySupplier,
			Set<DataCompression> availableArtifacts, DataCompression preferedArtifacts,
			Supplier<MetadataRepositoryFacade> metadataRepositoryProducer, Set<DataCompression> availableMetadata,
			DataCompression preferedMetadata) {
		this.path = path;
		this.artifactRepositoryFacadeSupplier = artifactRepositorySupplier;
		this.availableArtifacts = Collections.unmodifiableSet(availableArtifacts);
		this.preferedArtifacts = preferedArtifacts;
		this.metadataRepositoryFacadeSupplier = metadataRepositoryProducer;
		this.availableMetadata = Collections.unmodifiableSet(availableMetadata);
		this.preferedMetadata = preferedMetadata;
	}

	@Override
	public ArtifactRepositoryFacade getArtifactRepositoryFacade() throws IOException {
		return artifactRepositoryFacadeSupplier.get();
	}

	@Override
	public MetadataRepositoryFacade getMetadataRepositoryFacade() throws IOException {
		return metadataRepositoryFacadeSupplier.get();
	}

	@Override
	public Path getPath() {
		return path;
	}

	@Override
	public Set<DataCompression> getMetadataDataCompressions() {
		return availableMetadata;
	}

	@Override
	public Set<DataCompression> getArtifactDataCompressions() {
		return availableArtifacts;
	}

	@Override
	public void setCompression(DataCompression... compressions) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void accept(P2RepositoryVisitor visitor) {
		visitor.visit(this);
	}
}
