package com.github.pms1.tppt.jaxb;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

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
