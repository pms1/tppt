package com.github.pms1.tppt.p2.jaxb;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.github.pms1.ldap.SearchFilter;
import com.github.pms1.ldap.SearchFilterParser;
import com.github.pms1.ldap.SearchFilterPrinter;

public class SearchFilterAdapter extends XmlAdapter<String, SearchFilter> {

	private static SearchFilterParser parser = new SearchFilterParser().lenient();

	private static SearchFilterPrinter printer = new SearchFilterPrinter();

	@Override
	public SearchFilter unmarshal(String v) throws Exception {
		return parser.parse(v.trim());
	}

	@Override
	public String marshal(SearchFilter v) throws Exception {
		return printer.print(v);
	}

}
