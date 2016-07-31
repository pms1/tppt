package com.github.pms1.ldap;

public interface SearchFilter {

	<T> T accept(SearchFilterVisitor<T> visitor);
}
