package com.github.pms1.ocomp;

import java.lang.reflect.Type;
import java.util.function.BiFunction;

import com.github.pms1.ocomp.ObjectComparator.OPath2;

public interface DecomposerMatcher extends BiFunction<OPath2, Type, Boolean> {

}
