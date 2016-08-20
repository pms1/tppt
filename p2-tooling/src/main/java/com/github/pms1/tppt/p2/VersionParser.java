package com.github.pms1.tppt.p2;

import org.osgi.framework.Version;

public class VersionParser {

	public static Version valueOf(String v) {
		Version result = Version.valueOf(v);
		if (result == null)
			throw new IllegalArgumentException("Not a parseable version: " + v);

		String back = result.toString();
		if (!back.toString().equals(v) && !back.equals(v + ".0") && !back.equals(v + ".0.0"))
			throw new IllegalArgumentException("Inconsistent version: " + v + " " + result);

		return result;
	}
}
