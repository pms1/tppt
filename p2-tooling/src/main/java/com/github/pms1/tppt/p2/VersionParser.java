package com.github.pms1.tppt.p2;

import org.osgi.framework.Version;

public class VersionParser {

	static Version valueOf(String v) {
		Version result = Version.valueOf(v);
		if (result == null)
			throw new IllegalArgumentException("Not a parseable version: " + v);
		if (!result.toString().equals(v))
			throw new IllegalArgumentException("Inconsistent version: " + v + " " + result);
		return result;
	}
}
