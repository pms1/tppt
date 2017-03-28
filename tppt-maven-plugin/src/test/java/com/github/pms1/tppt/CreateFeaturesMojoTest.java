package com.github.pms1.tppt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import aQute.bnd.version.Version;

public class CreateFeaturesMojoTest {

	@Test
	public void x1() {
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.2", "foo")).isEqualTo(new Version(4, 6, 2, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.2-SNAPSHOT", "foo"))
				.isEqualTo(new Version(4, 6, 2, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.2.3", "foo")).isEqualTo(new Version(4, 6, 2, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.2.3-SNAPSHOT", "foo"))
				.isEqualTo(new Version(4, 6, 2, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6", "foo")).isEqualTo(new Version(4, 6, 0, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6-SNAPSHOT", "foo")).isEqualTo(new Version(4, 6, 0, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.x", "foo")).isEqualTo(new Version(4, 6, 0, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4", "foo")).isEqualTo(new Version(4, 0, 0, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4-SNAPSHOT", "foo")).isEqualTo(new Version(4, 0, 0, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.x", "foo")).isEqualTo(new Version(4, 0, 0, "foo"));
		assertThat(CreateFeaturesMojo.createOsgiVersion("bar", "foo")).isEqualTo(new Version(1, 0, 0, "foo"));
	}
}
