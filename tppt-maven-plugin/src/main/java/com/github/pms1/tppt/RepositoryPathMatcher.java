package com.github.pms1.tppt;

public interface RepositoryPathMatcher {

	boolean matches();

	<T> T get(String variable, Class<T> type);
}
