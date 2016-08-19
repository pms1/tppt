package com.github.pms1.ocomp;

import com.google.common.reflect.TypeToken;

public class DecomposerMatchers {

	public static DecomposerMatcher path(String s) {
		return (path, type) -> path.getPath().equals(s);
	}

	public static DecomposerMatcher isAssignable(TypeToken<?> typeToken) {
		return (path, type) -> typeToken.isAssignableFrom(type);
	}

}
