package com.github.pms1.tppt.core;

public interface RepositoryPathMatcher {

	boolean matches();

	<T> T get(String variable, Class<T> type);
}
