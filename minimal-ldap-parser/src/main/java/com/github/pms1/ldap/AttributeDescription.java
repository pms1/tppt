package com.github.pms1.ldap;

public class AttributeDescription implements Attribute {
	private final String keystring;

	AttributeDescription(String keystring) {
		this.keystring = keystring;
	}

	@Override
	public <T> T accept(AttributeVisitor<T> visitor) {
		return visitor.visit(this);
	}

	public String getKeystring() {
		return keystring;
	}
}
