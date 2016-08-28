package com.github.pms1.tppt.core;

import java.nio.file.Path;

public interface RepositoryPathPattern {

	RepositoryPathMatcher matcher(Path path);

}
