package com.github.pms1.tppt.p2.jaxb;

import org.osgi.framework.VersionRange;

import com.github.pms1.tppt.p2.VersionRangeParser;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class VersionRangeAdapter extends XmlAdapter<String, VersionRange> {

	@Override
	public VersionRange unmarshal(String v) throws Exception {
		return VersionRangeParser.valueOf(v);
	}

	@Override
	public String marshal(VersionRange v) throws Exception {
		if (v == null)
			return null;
		else
			return v.toString();
	}

}
