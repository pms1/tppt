package com.github.pms1.tppt;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ProcessEquinoxAppRunner implements EquinoxAppRunner {

	private final AppRunnerConfig config;
	private final Path launcher;
	private final Map<String, String> javaProperties;

	ProcessEquinoxAppRunner(AppRunnerConfig config, Path launcher, Map<String, String> javaProperties) {
		this.config = config;
		this.launcher = launcher;
		this.javaProperties = javaProperties;
	}

	static class FrameworkClassLoader extends URLClassLoader {

		public FrameworkClassLoader(URL[] urls) {
			super(urls, ClassLoader.getPlatformClassLoader());
		}

		@Override
		public void addURL(URL url) {
			super.addURL(url);
		}
	}

	@Override
	public int run(String app, String... args) {
		return run(null, app, args);
	}

	@Override
	public int run(InputStream is, String app, String... args) {
		try {
			Path tempDir = Files.createTempDirectory("maven-equinox-runner-");
			try {
				Path configuration = tempDir.resolve("configuration");

				Files.createDirectory(configuration);

				Properties p = new Properties();
				p.put("osgi.configuration.cascaded", "false");
				p.put("osgi.install.area", tempDir.toUri().toString());
				p.put("osgi.bundles.defaultStartLevel", "4");
				p.put("osgi.bundles", config.bundles().stream() //
						.map(p1 -> {
							String ref = "reference:" + p1.path();

							List<String> options = new ArrayList<>();
							if (p1.startLevel() != null)
								options.add(String.valueOf(p1.startLevel()));
							if (p1.autoStart())
								options.add("start");

							if (options != null)
								ref += "@" + String.join(":", options);

							return ref;
						}).collect(Collectors.joining(",")));
				p.put("osgi.framework", config.framework().toString());
				Path configIni = configuration.resolve("config.ini");
				try (OutputStream out = Files.newOutputStream(configIni)) {
					p.store(out, null);
				}

				List<String> command = new ArrayList<>();
				command.add(Paths.get(System.getProperty("java.home")).resolve("bin/java").toString());
				if (javaProperties != null)
					javaProperties.forEach((k, v) -> command.add("-D" + k + "=" + v));
				command.add("-jar");
				command.add(launcher.toString());
				command.add("-configuration");
				command.add(configuration.toString());
				command.add("-application");
				command.add(app);
//		if (logger.isDebugEnabled())
//			command.add("-debug");
				command.add("-consoleLog");
				command.add("-nosplash");
				Collections.addAll(command, args);
				System.err.println(command);
				Process pr = new ProcessBuilder(command).directory(tempDir.toFile()).start();

				CopyThread stdin = null;
				if (is != null) {
					stdin = new CopyThread(is, pr.getOutputStream());
					stdin.start();
				} else {
					stdin = null;
					pr.getOutputStream().close();
				}

				CopyThread stdout = new CopyThread(pr.getInputStream(), System.out);
				stdout.start();
				CopyThread stderr = new CopyThread(pr.getErrorStream(), System.err);
				stderr.start();
				int exitCode = pr.waitFor();
				if (stdin != null)
					stdin.join();
				stdout.join();
				stderr.join();

				return exitCode;
			} finally {
				try (Stream<Path> s = Files.walk(tempDir)) {
					s.sorted(Comparator.reverseOrder()) //
							.map(Path::toFile) //
							.forEach(File::delete);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
