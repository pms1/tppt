package com.github.pms1.ldap;

public interface Attribute {

	<T> T accept(AttributeVisitor<T> attributeVisitor);

}
