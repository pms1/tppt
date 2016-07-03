package com.github.pms1.tppt.jaxb;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "feature")
public class Feature {

	@XmlAttribute
	public String id;

	@XmlAttribute
	public String label;

	@XmlAttribute
	public String version;

	@XmlElement(name = "plugin")
	public Plugin[] plugins;

}
