package com.github.pms1.tppt;

import java.io.InputStream;

public interface EquinoxAppRunner {

	int run(String app, String... args);

	int run(InputStream is, String app, String... args);
}
