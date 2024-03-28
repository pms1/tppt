package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.inject.Named;
import javax.inject.Singleton;

@Named("jar")
@Singleton
public class DataCompressionJar implements DataCompression {

	@Override
	public String getFileSuffix() {
		return "jar";
	}

	@Override
	public String getP2IndexSuffix() {
		return "xml";
	}

	@Override
	public InputStream openInputStream(Path repository, String prefix) throws IOException {
		JarInputStream jar = new JarInputStream(Files.newInputStream(repository.resolve(prefix + ".jar")));

		String file = prefix + ".xml";

		for (ZipEntry e = jar.getNextEntry(); e != null; e = jar.getNextEntry()) {
			if (e.getName().equals(file))
				return jar;
		}

		throw new IllegalArgumentException("Did not contain '" + file + "'");
	}

	@Override
	public OutputStream openOutputStream(Path repository, String prefix) throws IOException {
		OutputStream os = Files.newOutputStream(repository.resolve(prefix + ".jar"));
		JarOutputStream jar = new JarOutputStream(os);

		JarEntry je = new JarEntry(prefix + ".xml");

		jar.putNextEntry(je);

		// jar.close() will close "os" too.
		return jar;
	}

	@Override
	public int getPriority() {
		return 100;
	}

}
