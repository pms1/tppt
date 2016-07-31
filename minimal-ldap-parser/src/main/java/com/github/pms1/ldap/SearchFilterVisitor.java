package com.github.pms1.ldap;

public interface SearchFilterVisitor<T> {

	T visit(SimpleSearchFilter simpleSearchFilter);

	T visit(AndSearchFilter andSearchFilter);

}
