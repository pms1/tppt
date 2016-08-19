package com.github.pms1.ocomp;

import java.lang.reflect.Type;
import java.util.function.Function;

import com.google.common.reflect.TypeToken;

public class DecomposerMatchers {

	public static DecomposerMatcher path(String s) {
		return (path, type) -> path.getPath().equals(s);
	}

	public static Function<Type, Boolean> isAssignable(TypeToken<?> typeToken) {
		return (type) -> typeToken.isAssignableFrom(type);
	}

}
