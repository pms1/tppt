package com.github.pms1.ocomp;

import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.github.pms1.ocomp.ObjectComparator.OPath2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class ObjectComparatorTest {

	static class A {
		int i;

		public A(int i) {
			this.i = i;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof A && i == ((A) obj).i;
		}

		@Override
		public int hashCode() {
			return 0;
		}
	}

	@Test
	public void test1() {

		List<ObjectDelta> delta = ObjectComparatorBuilder.newBuilder().build().compare(Arrays.asList(1, 2, 3),
				Arrays.asList(1, 2, 3));
		Assertions.assertThat(delta).isEmpty();
		delta = ObjectComparatorBuilder.newBuilder().build().compare(Arrays.asList(1, 2, 3), Arrays.asList(1, 2, 4));
		Assertions.assertThat(delta).size().isEqualTo(1);
		delta = ObjectComparatorBuilder.newBuilder().build().compare(Arrays.asList(1, 2, 3), Arrays.asList(1, 2));
		Assertions.assertThat(delta).size().isEqualTo(1);
		delta = ObjectComparatorBuilder.newBuilder().build().compare(Arrays.asList(1, 2, 3), Arrays.asList(1, 2, 3, 4));
		Assertions.assertThat(delta).size().isEqualTo(1);
	}

	@Test
	public void test3() {
		OPath2 p = OPath2.root("l1", "r1").child("/1", "l2", "r2").child("/2", "l3", "r3");

		Assertions.assertThat(p.size()).isEqualTo(3);
		Assertions.assertThat(p.subPath(0).getPath()).isEqualTo("//1/2");
		Assertions.assertThat(p.subPath(1).getPath()).isEqualTo("/1/2");
		Assertions.assertThat(p.subPath(2).getPath()).isEqualTo("/2");

		Assertions.assertThat(p.subPath(0, 1).getPath()).isEqualTo("/");
		Assertions.assertThat(p.subPath(1, 2).getPath()).isEqualTo("/1");
		Assertions.assertThat(p.subPath(2, 3).getPath()).isEqualTo("/2");
	}

	@Test
	public void test2() {

		Multimap<String, A> x = HashMultimap.create();
		x.put("1", new A(1));
		x.put("1", new A(2));
		x.put("1", new A(3));
		Multimap<String, A> y = HashMultimap.create();
		y.put("1", new A(3));
		y.put("1", new A(2));
		y.put("1", new A(1));
		System.err.println("diff");
		System.err.println(x.equals(y));
	}
}
