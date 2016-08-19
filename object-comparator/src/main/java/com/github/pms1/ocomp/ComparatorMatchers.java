package com.github.pms1.ocomp;

import com.google.common.reflect.TypeToken;

public class ComparatorMatchers {

	public static ComparatorMatcher isAssignable(TypeToken<?> typeToken) {
		return (type) -> typeToken.isAssignableFrom(type);
	}

}
