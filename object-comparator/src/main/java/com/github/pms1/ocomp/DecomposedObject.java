package com.github.pms1.ocomp;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import com.github.pms1.ocomp.ObjectComparator.OPath;
import com.google.common.collect.LinkedListMultimap;

public class DecomposedObject {
	LinkedListMultimap<OPath, TypedObject> decomposition = LinkedListMultimap.create();

	public boolean put(OPath content, Object value) {
		return decomposition.put(content, new TypedObject(value));
	}

	public boolean put(OPath content, Type type, Object value) {
		Objects.requireNonNull(type);
		return decomposition.put(content, new TypedObject(type, value));
	}

	public Set<OPath> keySet() {
		return decomposition.keySet();
	}

	public Collection<TypedObject> get(OPath key) {
		return decomposition.get(key);
	}

}
