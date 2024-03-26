package com.github.pms1.tppt.p2.jaxb;

import com.github.pms1.ldap.SearchFilter;
import com.github.pms1.ldap.SearchFilterParser;
import com.github.pms1.ldap.SearchFilterPrinter;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class SearchFilterAdapter extends XmlAdapter<String, SearchFilter> {

	private static SearchFilterParser parser = new SearchFilterParser().lenient();

	private static SearchFilterPrinter printer = new SearchFilterPrinter();

	@Override
	public SearchFilter unmarshal(String v) throws Exception {
		if (v == null)
			return null;
		else
			return parser.parse(v.trim());
	}

	@Override
	public String marshal(SearchFilter v) throws Exception {
		if (v == null)
			return null;
		else
			return printer.print(v);
	}

}
