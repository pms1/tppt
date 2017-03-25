package com.github.pms1.tppt.p2;

import java.nio.file.Path;

import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeProperty;
import com.google.common.base.Preconditions;

class CompositeRepositoryFacadeImpl extends AbstractRepositoryFacade<CompositeRepository>
		implements CompositeRepositoryFacade {
	private final CompositeRepository data;

	private final Path path;

	public CompositeRepositoryFacadeImpl(Path path, CompositeRepository data) {
		super(CompositeProperty::new);
		Preconditions.checkNotNull(data);
		this.data = data;
		this.path = path;
	}

	@Override
	public String toString() {
		return super.toString() + "(" + data + ")";
	}

	@Override
	public Path getPath() {
		return path;
	}

	@Override
	public CompositeRepository getRepository() {
		return data;
	}
}
