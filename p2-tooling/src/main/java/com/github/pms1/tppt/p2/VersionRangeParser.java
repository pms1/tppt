package com.github.pms1.tppt.p2;

import org.osgi.framework.VersionRange;

public class VersionRangeParser {

	public static VersionRange valueOf(String v) {
		VersionRange result = VersionRange.valueOf(v);
		if (result == null)
			throw new IllegalArgumentException("Not a parseable version: " + v);
		if (!result.toString().equals(v))
			throw new IllegalArgumentException("Inconsistent version: " + v + " " + result);
		return result;
	}
}
