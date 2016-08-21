package com.github.pms1.ocomp;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.github.pms1.ocomp.ObjectComparator.OPath2;

public class OPathMatcherTest {

	@Test
	public void t1() {
		OPathMatcher m = OPathMatcher.create("//foo[*]");

		OPath2 p1 = OPath2.root("lr", "rr").child("/foo", "lf", "rf").child("[foo]", "li", "ri");
		Assertions.assertThat(m.matches(p1)).isEqualTo(true);
		OPath2 p2 = p1.child("x", "lx", "rx");
		Assertions.assertThat(m.matches(p2)).isEqualTo(false);

		m = OPathMatcher.create("//foo[foo]");
		Assertions.assertThat(m.matches(p1)).isEqualTo(true);
		m = OPathMatcher.create("//foo[bar]");
		Assertions.assertThat(m.matches(p1)).isEqualTo(false);
	}
}
