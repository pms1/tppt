package com.github.pms1.ocomp;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;

import com.github.pms1.ocomp.ObjectComparator.OPath;
import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedListMultimap;

public class DecomposedMultimap implements DecomposedObject {
	private LinkedListMultimap<OPath, TypedObject> decomposition = LinkedListMultimap.create();
	public final boolean optimize;

	public DecomposedMultimap(boolean optimize) {
		this.optimize = optimize;
	}

	public DecomposedMultimap() {
		this(true);
	}

	public void put(OPath key, Object value) {
		Preconditions.checkNotNull(key);
		decomposition.put(key, new TypedObject(value));
	}

	public void put(OPath key, Type type, Object value) {
		Preconditions.checkNotNull(key);
		decomposition.put(key, new TypedObject(type, value));
	}

	public Set<OPath> keySet() {
		return decomposition.keySet();
	}

	public Collection<TypedObject> get(OPath key) {
		Preconditions.checkNotNull(key);
		return decomposition.get(key);
	}

}
