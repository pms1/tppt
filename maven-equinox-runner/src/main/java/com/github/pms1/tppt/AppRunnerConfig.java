package com.github.pms1.tppt;

import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Objects;

record AppRunnerConfig(URL framework, List<AppBundle> bundles) implements Serializable {
	AppRunnerConfig {
		Objects.requireNonNull(framework);
		Objects.requireNonNull(bundles);
	}
}
