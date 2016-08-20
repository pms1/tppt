package com.github.pms1.ldap;

import java.util.Objects;
import java.util.function.Function;

public class SearchFilterEvaluator {

	public boolean evaluate(SearchFilter filter, Function<Attribute, String> dataProvider) {

		return filter.accept(new SearchFilterVisitor<Boolean>() {

			@Override
			public Boolean visit(SimpleSearchFilter simpleSearchFilter) {
				String value = dataProvider.apply(simpleSearchFilter.getAttribute());

				switch (simpleSearchFilter.getFilterType()) {
				case EQUAL:
					return Objects.equals(value, simpleSearchFilter.getAssertionValue());
				default:
					throw new IllegalStateException();
				}

			}

			@Override
			public Boolean visit(AndSearchFilter andSearchFilter) {
				for (SearchFilter child : andSearchFilter.getChildren())
					if (!child.accept(this))
						return false;

				return true;
			}

			@Override
			public Boolean visit(OrSearchFilter orSearchFilter) {
				for (SearchFilter child : orSearchFilter.getChildren())
					if (child.accept(this))
						return true;

				return false;
			}

			@Override
			public Boolean visit(NotSearchFilter notSearchFilter) {
				return !notSearchFilter.getChild().accept(this);
			}
		});
	}
}
