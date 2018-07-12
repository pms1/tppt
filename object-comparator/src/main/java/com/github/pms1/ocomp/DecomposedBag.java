package com.github.pms1.ocomp;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;

public class DecomposedBag implements DecomposedObject {
	private Collection<TypedObject> data = new ArrayList<>();

	public void put(Object value) {
		data.add(new TypedObject(value));
	}

	public void put(Type type, Object value) {
		data.add(new TypedObject(type, value));
	}

	public Collection<TypedObject> get() {
		return data;
	}

}
