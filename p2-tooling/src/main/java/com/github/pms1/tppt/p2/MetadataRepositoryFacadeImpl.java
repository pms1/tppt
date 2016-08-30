package com.github.pms1.tppt.p2;

import java.nio.file.Path;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.google.common.base.Preconditions;

public class MetadataRepositoryFacadeImpl implements MetadataRepositoryFacade {
	private final MetadataRepository data;

	private final Path path;

	public MetadataRepositoryFacadeImpl(Path path, MetadataRepository foo) {
		Preconditions.checkNotNull(path);
		Preconditions.checkNotNull(foo);
		this.data = foo;
		this.path = path;
	}

	@Override
	public String toString() {
		return super.toString() + "(" + data + ")";
	}

	@Override
	public MetadataRepository getMetadata() {
		return data;
	}

	@Override
	public Path getPath() {
		return path;
	}
}
