package com.github.pms1.ocomp;

import java.lang.reflect.Type;

import com.google.common.base.Preconditions;

class TypedObject {
	 final private Type type;
	 final private Object value;
	 
	 TypedObject(Object value) {
		 this.type = null;
		 this.value = value;
	 }
	 
	 TypedObject(Type type, Object value) {
		 Preconditions.checkNotNull(type);
		 this.type = type;
		 this.value = value;
	 }

	public Object getValue() {
		return value;
	}

	public Type getType() {
		return type;
	}
}
