package com.github.pms1.ocomp;

public interface Decomposer<T> {
	DecomposedObject decompose(T o);
}