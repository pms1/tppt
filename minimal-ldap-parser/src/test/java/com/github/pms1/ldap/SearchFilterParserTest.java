package com.github.pms1.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SearchFilterParserTest {
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	SearchFilterParser parser = new SearchFilterParser();

	SearchFilterPrinter printer = new SearchFilterPrinter();

	@Test
	public void and() {
		SearchFilter parse = parser.parse("(&(a=5))");
		assertThat(printer.print(parse)).isEqualTo("(&(a=5))");
	}

	@Test
	public void and2() {
		SearchFilter parse = parser.parse("(&(a=5)(b=Foo))");
		assertThat(printer.print(parse)).isEqualTo("(&(a=5)(b=Foo))");
	}

	@Test
	public void simple() {
		SearchFilter parse = parser.parse("(a=5)");
		assertThat(printer.print(parse)).isEqualTo("(a=5)");
	}

	@Test
	public void failMissingClosingParen() {
		thrown.expect(RuntimeException.class);
		parser.parse("(&(a=5)(b=Foo)");
	}
}
