package com.github.pms1.tppt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.osgi.framework.BundleException;

import com.github.pms1.tppt.EquinoxRunner.Plugin;

@Component(role = EquinoxRunnerFactory.class)
public class EquinoxRunnerFactory {

	class EquinoxRunnerBuilderImpl implements EquinoxRunnerBuilder {

		private Set<Path> installations = new HashSet<>();
		private Set<Plugin> bundles = new HashSet<>();

		@Override
		public EquinoxRunnerBuilder withInstallation(Path path) {
			installations.add(path);
			return this;
		}

		@Override
		public EquinoxRunner build() throws IOException {
			Set<Plugin> plugins = new HashSet<>();

			for (Path path : installations) {
				Files.list(path.resolve("plugins")).forEach(p -> {

					try {
						Plugin p1 = EquinoxRunner.scanPlugin(p);
						if (p1 != null)
							plugins.add(p1);
					} catch (IOException | BundleException e) {
						throw new RuntimeException(e);
					}
				});
			}

			plugins.addAll(bundles);

			return new EquinoxRunner(plugins);
		}

		@Override
		public EquinoxRunnerBuilder withPlugin(Path path) throws IOException {
			Plugin bundle;
			try {
				bundle = EquinoxRunner.scanPlugin(path);
			} catch (BundleException e) {
				throw new RuntimeException(e);
			}

			if (bundle == null)
				throw new IllegalArgumentException("Not an OSGi bundle: " + path);
			bundles.add(bundle);
			return this;
		}

	}

	public EquinoxRunnerBuilder newBuilder() {
		return new EquinoxRunnerBuilderImpl();
	}

}
