package com.github.pms1.tppt;

import java.nio.file.Path;

public interface RepositoryPathPattern {

	RepositoryPathMatcher matcher(Path path);

}
