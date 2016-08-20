package com.github.pms1.ldap;

public class NotSearchFilter implements SearchFilter {
	private final SearchFilter child;

	public NotSearchFilter(SearchFilter child) {
		this.child = child;
	}

	public SearchFilter getChild() {
		return child;
	}

	@Override
	public <T> T accept(SearchFilterVisitor<T> visitor) {
		return visitor.visit(this);
	}
}
