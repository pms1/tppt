package com.github.pms1.tppt.p2;

import java.nio.file.Path;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Property;
import com.google.common.base.Preconditions;

public class MetadataRepositoryFacadeImpl extends AbstractRepositoryFacade<MetadataRepository>
		implements MetadataRepositoryFacade {
	private final MetadataRepository data;

	private final Path path;

	public MetadataRepositoryFacadeImpl(Path path, MetadataRepository foo) {
		super(Property::new);
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
	public MetadataRepository getRepository() {
		return data;
	}

	@Override
	public Path getPath() {
		return path;
	}
}
