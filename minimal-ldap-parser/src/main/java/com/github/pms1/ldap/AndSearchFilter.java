package com.github.pms1.ldap;

import java.util.List;

public class AndSearchFilter implements SearchFilter {
	private final List<SearchFilter> children;

	public AndSearchFilter(List<SearchFilter> children) {
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
