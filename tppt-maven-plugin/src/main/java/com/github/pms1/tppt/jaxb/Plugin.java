package com.github.pms1.tppt.jaxb;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.github.pms1.ldap.SearchFilter;
import com.github.pms1.tppt.p2.jaxb.SearchFilterAdapter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
public class Plugin {

	@XmlAttribute
	public String id;

	@XmlAttribute
	@XmlJavaTypeAdapter(SearchFilterAdapter.class)
	public SearchFilter filter;

	@XmlAttribute
	public String os;

	@XmlAttribute
	public String ws;

	@XmlAttribute
	public String nl;

	@XmlAttribute
	public String arch;

	@XmlAttribute(name = "download-size")
	public Long download_size;

	@XmlAttribute(name = "install-size")
	public Long install_size;

	@XmlAttribute
	public String version;

	@XmlAttribute
	public Boolean fragment;

	@XmlAttribute
	public Boolean unpack;

}