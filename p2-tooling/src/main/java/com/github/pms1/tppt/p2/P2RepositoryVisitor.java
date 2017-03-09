package com.github.pms1.tppt.p2;

public interface P2RepositoryVisitor {
	void visit(P2Repository repo);

	void visit(P2CompositeRepository repo);
}
