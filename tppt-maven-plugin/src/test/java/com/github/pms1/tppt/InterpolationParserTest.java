package com.github.pms1.tppt;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class InterpolationParserTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void test() {
		assertThat(asString(InterpolatedString.parse("foo")), equalTo("[text:foo]"));
		assertThat(asString(InterpolatedString.parse("foo @{a}")), equalTo("[text:foo ][variable:[a]]"));
		assertThat(asString(InterpolatedString.parse("foo @{a} bar")),
				CoreMatchers.equalTo("[text:foo ][variable:[a]][text: bar]"));
		assertThat(asString(InterpolatedString.parse("a\\@b")), equalTo("[text:a@b]"));
		assertThat(asString(InterpolatedString.parse("foo @{a:b} bar")),
				CoreMatchers.equalTo("[text:foo ][variable:[a, b]][text: bar]"));
		assertThat(asString(InterpolatedString.parse("foo @{a\\:b} bar")),
				CoreMatchers.equalTo("[text:foo ][variable:[a:b]][text: bar]"));
	}

	@Test
	public void variableOnly() {
		assertThat(asString(InterpolatedString.parse("@{a}")), equalTo("[variable:[a]]"));
	}

	@Test
	public void fail1() {
		thrown.expect(IllegalArgumentException.class);
		InterpolatedString.parse("foo@bar");
	}

	@Test
	public void fail2() {
		thrown.expect(IllegalArgumentException.class);
		InterpolatedString.parse("foo@{var");
	}

	@Test
	public void fail3() {
		thrown.expect(IllegalArgumentException.class);
		InterpolatedString.parse("foo@{}bar");
	}

	String asString(InterpolatedString is) {
		TextVisitor t = new TextVisitor();
		is.accept(t);
		return t.result.toString();
	}

	static class TextVisitor implements InterpolatedString.Visitor {
		StringBuilder result = new StringBuilder();

		@Override
		public void visitText(String text) {
			result.append("[text:" + text + "]");
		}

		@Override
		public void visitVariable(List<String> variable) {
			result.append("[variable:" + variable + "]");
		}

	}
}
