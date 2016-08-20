package com.github.pms1.ldap;

import java.util.List;

public class OrSearchFilter implements SearchFilter {
	private final List<SearchFilter> children;

	public OrSearchFilter(List<SearchFilter> children) {
		this.children = children;
	}

	public List<SearchFilter> getChildren() {
		return children;
	}

	@Override
	public <T> T accept(SearchFilterVisitor<T> visitor) {
		return visitor.visit(this);
	}
}
