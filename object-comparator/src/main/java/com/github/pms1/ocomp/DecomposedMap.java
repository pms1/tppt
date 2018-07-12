package com.github.pms1.ocomp;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.github.pms1.ocomp.ObjectComparator.OPath;

public class DecomposedMap implements DecomposedObject {
	private Map<OPath, TypedObject> decomposition = new LinkedHashMap<>();

	public void put(OPath content, Object value) {
		if (decomposition.putIfAbsent(content, new TypedObject(value)) != null)
			throw new IllegalArgumentException("Duplicate key: " + content);
	}

	public void put(OPath content, Type type, Object value) {
		if (decomposition.putIfAbsent(content, new TypedObject(type, value)) != null)
			throw new IllegalArgumentException("Duplicate key: " + content);
	}

	public Set<OPath> keySet() {
		return decomposition.keySet();
	}

	public Collection<TypedObject> get(OPath key) {
		return decomposition.containsKey(key) ? Collections.singleton(decomposition.get(key)) : Collections.emptyList();
	}

}
