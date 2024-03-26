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
import java.util.Arrays;

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

		System.err.println("FCL " + Arrays.toString(urls));

		try (FrameworkClassLoader cl = new FrameworkClassLoader(urls)) {
			Class<?> m2 = cl.loadClass(M2.class.getName());

			Method run = m2.getMethod("run", byte[].class, String.class, String[].class);

			try {
				return (int) run.invoke(null, serializedConfig, app, args);
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
			throw new UncheckedIOException(e);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
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
