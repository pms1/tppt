package com.github.pms1.tppt.p2.jaxb;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.osgi.framework.Version;

import com.github.pms1.tppt.p2.VersionParser;

public class VersionAdapter extends XmlAdapter<String, Version> {

	@Override
	public Version unmarshal(String v) throws Exception {
		return VersionParser.valueOf(v);
	}

	@Override
	public String marshal(Version v) throws Exception {
		return v.toString();
	}

}
