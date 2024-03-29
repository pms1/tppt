package com.github.pms1.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.Test;

public class SearchFilterParserTest {
	SearchFilterParser parser = new SearchFilterParser();
	SearchFilterParser parserLenient = new SearchFilterParser().lenient();

	SearchFilterPrinter printer = new SearchFilterPrinter();

	@Test
	public void andStrict() {
		SearchFilter parse = parser.parse("(&(a=5))");
		assertThat(printer.print(parse)).isEqualTo("(&(a=5))");
	}

	@Test
	public void andStrictSpace() {
		assertThatExceptionOfType(SearchFilterParseException.class).isThrownBy(() -> {
			parser.parse("(& (a=5))");
		});
	}

	@Test
	public void andLenient() {
		SearchFilter parse = parserLenient.parse("(&(a=5))");
		assertThat(printer.print(parse)).isEqualTo("(&(a=5))");
	}

	@Test
	public void andLenientSpace() {
		SearchFilter parse = parserLenient.parse("(& (a=5))");
		assertThat(printer.print(parse)).isEqualTo("(&(a=5))");
	}

	@Test
	public void andLenientSpaceTwo() {
		SearchFilter parse = parserLenient.parse("(& (a=5) (b=6))");
		assertThat(printer.print(parse)).isEqualTo("(&(a=5)(b=6))");
	}

	@Test
	public void twoAnd() {
		SearchFilter parse = parser.parse("(&(a=5)(b=Foo))");
		assertThat(printer.print(parse)).isEqualTo("(&(a=5)(b=Foo))");
	}

	@Test
	public void simple() {
		SearchFilter parse = parser.parse("(a=5)");
		assertThat(printer.print(parse)).isEqualTo("(a=5)");
	}

	@Test
	public void dotStrict() {
		assertThatExceptionOfType(SearchFilterParseException.class).isThrownBy(() -> {
			parser.parse("(a.b=5)");
		});
	}

	@Test
	public void dotLenient() {
		SearchFilter parse = parserLenient.parse("(a.b=5)");
		assertThat(printer.print(parse)).isEqualTo("(a.b=5)");
	}

	@Test
	public void dotLeft() {
		SearchFilter parse = parser.parse("(a=b.c)");
		assertThat(printer.print(parse)).isEqualTo("(a=b.c)");
		parse = parserLenient.parse("(a=b.c)");
		assertThat(printer.print(parse)).isEqualTo("(a=b.c)");
	}

	@Test
	public void not() {
		SearchFilter parse = parser.parse("(&(a=b)(!(c=d)))");
		assertThat(printer.print(parse)).isEqualTo("(&(a=b)(!(c=d)))");
	}

	@Test
	public void exclamationLeft() {
		SearchFilter parse = parser.parse("(a=b!c)");
		assertThat(printer.print(parse)).isEqualTo("(a=b!c)");
		parse = parserLenient.parse("(a=b!c)");
		assertThat(printer.print(parse)).isEqualTo("(a=b!c)");
	}

	@Test
	public void orStrict() {
		SearchFilter parse = parser.parse("(|(a=5))");
		assertThat(printer.print(parse)).isEqualTo("(|(a=5))");
	}

	@Test
	public void twoOr() {
		SearchFilter parse = parser.parse("(|(a=5)(b=Foo))");
		assertThat(printer.print(parse)).isEqualTo("(|(a=5)(b=Foo))");
	}

	@Test
	public void pipeLeft() {
		SearchFilter parse = parser.parse("(a=b|c)");
		assertThat(printer.print(parse)).isEqualTo("(a=b|c)");
		parse = parserLenient.parse("(a=b|c)");
		assertThat(printer.print(parse)).isEqualTo("(a=b|c)");
	}

	@Test
	public void asteriskLeft() {
		SearchFilter parse = parser.parse("(a=b*c)");
		assertThat(printer.print(parse)).isEqualTo("(a=b*c)");
		parse = parserLenient.parse("(a=b*c)");
		assertThat(printer.print(parse)).isEqualTo("(a=b*c)");
	}

	@Test
	public void strictSpaceEnd() {
		assertThatExceptionOfType(SearchFilterParseException.class).isThrownBy(() -> {
			parser.parse("(&(a=b) )");
		});
	}

	@Test
	public void lenientSpaceEnd() {
		SearchFilter parse = parserLenient.parse("(&(a=b) )");
		assertThat(printer.print(parse)).isEqualTo("(&(a=b))");
	}

	@Test
	public void failMissingClosingParen() {
		assertThatExceptionOfType(SearchFilterParseException.class).isThrownBy(() -> {
			parser.parse("(&(a=5)(b=Foo)");
		});
	}
}
