package com.github.pms1.tppt.mirror.jaxb;

import java.net.URI;

import javax.xml.bind.annotation.XmlAttribute;

public class Mirror {
	@XmlAttribute
	public URI url;

	@Override
	public String toString() {
		return "Mirror(url=" + url + ")";
	}
}