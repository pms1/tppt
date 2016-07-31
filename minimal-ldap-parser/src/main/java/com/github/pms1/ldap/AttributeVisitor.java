package com.github.pms1.ldap;

public interface AttributeVisitor<T> {

	T visit(AttributeDescription attributeDescription);

}
