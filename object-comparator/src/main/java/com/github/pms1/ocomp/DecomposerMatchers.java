package com.github.pms1.ocomp;

import java.lang.reflect.Type;
import java.util.function.Function;

import com.google.common.reflect.TypeToken;

public class DecomposerMatchers {

	public static DecomposerMatcher path(String s) {
		OPathMatcher matcher = OPathMatcher.create(s);
		return (path, type) -> matcher.matches(path);
	}

	public static Function<Type, Boolean> isAssignable(TypeToken<?> typeToken) {
		return (type) -> typeToken.isAssignableFrom(type);
	}

}
