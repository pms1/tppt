package com.github.pms1.tppt.mirror.jaxb;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class Proxy {

	@XmlElement
	public String protocol;
	@XmlElement
	public String host;
	@XmlElement
	public Integer port;
	@XmlElement
	public String username;
	@XmlElement
	public String password;
	@XmlElement(name = "nonProxyHost")
	public List<String> nonProxyHosts;
}
