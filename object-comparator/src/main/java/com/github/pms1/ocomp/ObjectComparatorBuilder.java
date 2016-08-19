package com.github.pms1.ocomp;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.pms1.ocomp.ObjectComparator.DecomposerFactory;
import com.github.pms1.ocomp.ObjectComparator.DeltaCreator;

public class ObjectComparatorBuilder<T> {

	private LinkedHashMap<DecomposerMatcher, DecomposerFactory> locationDecomposers = new LinkedHashMap<>();
	private LinkedHashMap<Function<Type, Boolean>, BiFunction<Object, Object, Boolean>> comparators = new LinkedHashMap<>();

	private DeltaCreator<T> deltaCreator = (DeltaCreator<T>) ObjectComparator.defaultDeltaCreator;

	private ObjectComparatorBuilder() {

	}

	public static ObjectComparatorBuilder<ObjectDelta> newBuilder() {
		return new ObjectComparatorBuilder<>();
	}

	public ObjectComparator<T> build() {
		return new ObjectComparator<T>(deltaCreator, comparators, locationDecomposers);
	}

	public <S> ObjectComparatorBuilder<T> addComparator(Function<Type, Boolean> matcher,
			BiFunction<Object, Object, Boolean> comparator) {
		comparators.put(matcher, comparator);
		return this;
	}

	public <S> ObjectComparatorBuilder<T> addDecomposer(String string, Decomposer<S> listToMapDecomposer) {
		return addDecomposer(DecomposerMatchers.path(string), listToMapDecomposer);
	}

	public <S> ObjectComparatorBuilder<T> addDecomposer(DecomposerMatcher matcher, Decomposer<S> listToMapDecomposer) {
		locationDecomposers.put(matcher, new DecomposerFactory() {

			@Override
			public <T> Decomposer<T> generate(Type t) {
				return (Decomposer<T>) listToMapDecomposer;
			}
		});
		return this;
	}

	public ObjectComparatorBuilder<T> addDecomposer(Function<Type, Boolean> matcher, DecomposerFactory factory) {
		locationDecomposers.put((path, type) -> matcher.apply(type), factory);
		return this;
	}

	public <S> ObjectComparatorBuilder<S> setDeltaCreator(DeltaCreator<S> deltaCreator) {
		this.deltaCreator = (DeltaCreator<T>) deltaCreator;
		return (ObjectComparatorBuilder<S>) this;
	}

}
