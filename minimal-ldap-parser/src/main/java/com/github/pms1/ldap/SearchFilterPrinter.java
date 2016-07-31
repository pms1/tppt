package com.github.pms1.ldap;

public class SearchFilterPrinter {

	private final SearchFilterVisitor<String> searchFilterVisitor = new SearchFilterVisitor<String>() {

		@Override
		public String visit(AndSearchFilter andSearchFilter) {
			return print(andSearchFilter);
		}

		@Override
		public String visit(SimpleSearchFilter simpleSearchFilter) {
			return print(simpleSearchFilter);
		}
	};

	public String print(SearchFilter filter) {
		return filter.accept(searchFilterVisitor);
	}

	public String print(AndSearchFilter andSearchFilter) {
		StringBuilder b = new StringBuilder();
		b.append("(&");
		for (SearchFilter f : andSearchFilter.getChildren())
			b.append(print(f));
		b.append(")");
		return b.toString();
	}

	public String print(SimpleSearchFilter simpleSearchFilter) {
		StringBuilder b = new StringBuilder();
		b.append("(");
		b.append(print(simpleSearchFilter.getAttribute()));
		switch (simpleSearchFilter.getFilterType()) {
		case EQUAL:
			b.append("=");
			break;
		default:
			throw new IllegalStateException();
		}
		b.append(simpleSearchFilter.getAssertionValue());
		b.append(")");
		return b.toString();
	}

	private final AttributeVisitor<String> attributeVisitor = new AttributeVisitor<String>() {

		@Override
		public String visit(AttributeDescription attributeDescription) {
			return print(attributeDescription);
		}

	};

	public String print(Attribute attribute) {
		return attribute.accept(attributeVisitor);
	}

	public String print(AttributeDescription attributeDescription) {
		return attributeDescription.getKeystring();
	}
}
