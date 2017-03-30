package com.github.pms1.tppt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import aQute.bnd.version.Version;

public class CreateFeaturesMojoTest {

	@Test
	public void x1() {
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.2")).isEqualTo(new Version(4, 6, 2));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.2-SNAPSHOT")).isEqualTo(new Version(4, 6, 2));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.2.3")).isEqualTo(new Version(4, 6, 2));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.2.3-SNAPSHOT")).isEqualTo(new Version(4, 6, 2));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6")).isEqualTo(new Version(4, 6, 0));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6-SNAPSHOT")).isEqualTo(new Version(4, 6, 0));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.6.x")).isEqualTo(new Version(4, 6, 0));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4")).isEqualTo(new Version(4, 0, 0));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4-SNAPSHOT")).isEqualTo(new Version(4, 0, 0));
		assertThat(CreateFeaturesMojo.createOsgiVersion("4.x")).isEqualTo(new Version(4, 0, 0));
		assertThat(CreateFeaturesMojo.createOsgiVersion("bar")).isEqualTo(new Version(1, 0, 0));
	}
}
