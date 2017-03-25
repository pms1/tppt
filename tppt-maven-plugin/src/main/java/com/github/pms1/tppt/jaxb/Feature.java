package com.github.pms1.tppt.jaxb;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@XmlRootElement(name = "feature")
@SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
public class Feature {

	@XmlElement
	public String description;

	@XmlAttribute
	public String id;

	@XmlAttribute
	public String label;

	@XmlAttribute
	public String version;

	@XmlElement(name = "plugin")
	public Plugin[] plugins;

}
