package com.github.pms1.tppt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

class EmbeddedEquinoxAppRunner implements EquinoxAppRunner {

	private final AppRunnerConfig config;

	EmbeddedEquinoxAppRunner(AppRunnerConfig config) {
		this.config = config;
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

	private static final String clResource = EmbeddedEquinoxAppRunner.class.getName().replace('.', '/') + ".class";

	@Override
	public int run(String app, String... args) {

		URL resource = getClass().getClassLoader().getResource(clResource);
		URL resourceJar = unjar(resource);

		URL frameworkUrl = config.framework();

		byte[] serializedConfig;

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(config);
			oos.flush();
			baos.flush();
			serializedConfig = baos.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		URL[] urls = new URL[] { resourceJar, frameworkUrl };

		Path tempDir;
		try {
			tempDir = Files.createTempDirectory("maven-equinox-runnner-");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		try (FrameworkClassLoader cl = new FrameworkClassLoader(urls)) {
			Class<?> m2 = cl.loadClass(FrameworkStarter.class.getName());

			Method run = m2.getMethod("run", Path.class, byte[].class, String.class, String[].class);

			try {
				return (int) run.invoke(null, tempDir, serializedConfig, app, args);
			} catch (InvocationTargetException e) {
				try {
					throw e.getCause();
				} catch (RuntimeException | Error e1) {
					throw e1;
				} catch (Throwable t) {
					throw e;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to close class loader: " + e, e);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		} finally {
			deleteTempDirectory(tempDir);
		}
	}

	static void deleteTempDirectory(Path tempDir) {
		try (Stream<Path> s = Files.walk(tempDir)) {
			boolean[] didGc = new boolean[] { false };
			s.sorted(Comparator.reverseOrder()) //
					.forEach(p -> {
						try {
							try {
								Files.deleteIfExists(p);
							} catch (FileSystemException e) {
								if (!didGc[0]) {
									System.gc();
									didGc[0] = true;
									Files.deleteIfExists(p);
								}
							}

						} catch (DirectoryNotEmptyException e) {
							if (!didGc[0])
								LoggerFactory.getLogger(EmbeddedEquinoxAppRunner.class)
										.warn("Failed to delete: " + p + ": " + e, e);
						} catch (IOException e) {
							LoggerFactory.getLogger(EmbeddedEquinoxAppRunner.class)
									.warn("Failed to delete: " + p + ": " + e, e);
						}

					});
		} catch (IOException e) {
			LoggerFactory.getLogger(EmbeddedEquinoxAppRunner.class)
					.warn("Failed to cleanup temporary directory: " + tempDir + ": " + e, e);
		}
	}

	static URL unjar(URL url) {
		if (url.getProtocol() == null)
			throw new IllegalArgumentException("URL:" + url);

		switch (url.getProtocol()) {
		case "jar":
			String path = url.getPath();
			int idx = path.indexOf("!/");
			if (idx == -1)
				throw new IllegalArgumentException("URL:" + url);
			if (!path.substring(idx + 2).equals(clResource))
				throw new IllegalArgumentException("URL:" + url);
			try {
				return new URL(path.substring(0, idx));
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("URL:" + url + ": " + e, e);
			}
		case "file":
			String flat = url.toString().replace(File.separatorChar, '/');
			if (!flat.endsWith("/" + clResource))
				throw new IllegalArgumentException("URL:" + url);
			try {
				return new URL(flat.substring(0, flat.length() - clResource.length()));
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("URL:" + url);
			}
		default:
			throw new IllegalArgumentException("URL:" + url);
		}
	}

	@Override
	public int run(InputStream is, String app, String... args) {
		throw new UnsupportedOperationException();
	}
}
