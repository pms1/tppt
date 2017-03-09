package com.github.pms1.tppt.p2;

import java.nio.file.Path;

import com.github.pms1.tppt.p2.jaxb.Repository;

public interface RepositoryFacade {
	Repository getRepository();

	Path getPath();
}
