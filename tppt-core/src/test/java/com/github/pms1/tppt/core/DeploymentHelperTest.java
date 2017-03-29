package com.github.pms1.tppt.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class DeploymentHelperTest {

	@Test
	public void test1() {
		assertThat(DeploymentHelper.asRegularExpression("yyyyMMddHHmmss"))
				.isEqualTo("\\d{4}\\d{2}\\d{2}\\d{2}\\d{2}\\d{2}");
		assertThat(DeploymentHelper.asRegularExpression("yyMMddHHmmss"))
				.isEqualTo("\\d{2}\\d{2}\\d{2}\\d{2}\\d{2}\\d{2}");
		assertThat(DeploymentHelper.asRegularExpression("yyyyMMddHHmm")).isEqualTo("\\d{4}\\d{2}\\d{2}\\d{2}\\d{2}");
	}
}
