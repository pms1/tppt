package com.github.pms1.tppt.p2.jaxb;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.osgi.framework.VersionRange;

import com.github.pms1.tppt.p2.VersionRangeParser;

public class VersionRangeAdapter extends XmlAdapter<String, VersionRange> {

	@Override
	public VersionRange unmarshal(String v) throws Exception {
		return VersionRangeParser.valueOf(v);
	}

	@Override
	public String marshal(VersionRange v) throws Exception {
		return v.toString();
	}

}
