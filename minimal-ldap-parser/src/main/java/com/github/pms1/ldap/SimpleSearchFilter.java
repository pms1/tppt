package com.github.pms1.ldap;

public class SimpleSearchFilter implements SearchFilter {

	private final Attribute attribute;
	private final FilterType filterType;
	private final String assertionValue;

	public SimpleSearchFilter(Attribute attribute, FilterType filterType, String assertionValue) {
		this.attribute = attribute;
		this.filterType = filterType;
		this.assertionValue = assertionValue;
	}

	@Override
	public <T> T accept(SearchFilterVisitor<T> visitor) {
		return visitor.visit(this);
	}

	public FilterType getFilterType() {
		return filterType;
	}

	public Attribute getAttribute() {
		return attribute;
	}

	public String getAssertionValue() {
		return assertionValue;
	}

}
