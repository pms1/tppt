package com.github.pms1.tppt.p2;

import org.codehaus.plexus.PlexusTestCase;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class PlexusContainerRule implements TestRule {

	private static class PlexusJunit4 extends PlexusTestCase {
		public <T extends Object> T lookup(java.lang.Class<T> componentClass, String roleHint) throws Exception {
			return super.lookup(componentClass, roleHint);
		}

		@Override
		protected <T> T lookup(Class<T> componentClass) throws Exception {
			return super.lookup(componentClass);
		}

		@Override
		protected void setUp() throws Exception {
			super.setUp();
		}

		@Override
		protected void tearDown() throws Exception {
			super.tearDown();
		}

	};

	private PlexusJunit4 plexus;

	@Override
	public Statement apply(Statement base, Description description) {

		return new Statement() {

			@Override
			public void evaluate() throws Throwable {
				if (plexus != null)
					throw new IllegalStateException();
				try {
					plexus = new PlexusJunit4();
					plexus.setUp();
					base.evaluate();
				} finally {
					plexus.tearDown();
				}
			}

		};
	}

	public <T> T lookup(Class<T> componentClass) throws Exception {
		if (plexus == null)
			throw new IllegalStateException();
		return plexus.lookup(componentClass);
	}

	public <T> T lookup(Class<T> componentClass, String roleHint) throws Exception {
		if (plexus == null)
			throw new IllegalStateException();
		return plexus.lookup(componentClass, roleHint);
	}
}
