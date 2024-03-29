package com.github.pms1.tppt.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.junit.Test;

public class InterpolationParserTest {

	@Test
	public void test() {
		assertThat(asString(InterpolatedString.parse("foo"))).isEqualTo("[text:foo]");
		assertThat(asString(InterpolatedString.parse("foo @{a}"))).isEqualTo("[text:foo ][variable:[a]]");
		assertThat(asString(InterpolatedString.parse("foo @{a} bar")))
				.isEqualTo("[text:foo ][variable:[a]][text: bar]");
		assertThat(asString(InterpolatedString.parse("a\\@b"))).isEqualTo("[text:a@b]");
		assertThat(asString(InterpolatedString.parse("foo @{a:b} bar")))
				.isEqualTo("[text:foo ][variable:[a, b]][text: bar]");
		assertThat(asString(InterpolatedString.parse("foo @{a\\:b} bar")))
				.isEqualTo("[text:foo ][variable:[a:b]][text: bar]");
	}

	@Test
	public void variableOnly() {
		assertThat(asString(InterpolatedString.parse("@{a}")), equalTo("[variable:[a]]"));
	}

	@Test
	public void fail1() {
		assertThatIllegalArgumentException().isThrownBy(() -> {
			InterpolatedString.parse("foo@bar");
		});
	}

	@Test
	public void fail2() {
		assertThatIllegalArgumentException().isThrownBy(() -> {
			InterpolatedString.parse("foo@{var");
		});
	}

	@Test
	public void fail3() {
		assertThatIllegalArgumentException().isThrownBy(() -> {
			InterpolatedString.parse("foo@{}bar");
		});
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
