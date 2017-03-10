package com.github.pms1.tppt.p2;

public interface P2RepositoryVisitor<T> {
	T visit(P2Repository repo);

	T visit(P2CompositeRepository repo);
}
