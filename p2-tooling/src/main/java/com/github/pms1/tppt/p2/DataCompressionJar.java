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

import org.codehaus.plexus.component.annotations.Component;

@Component(role = DataCompression.class, hint = "jar")
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

		for (ZipEntry e = jar.getNextEntry(); e != null; e = jar.getNextEntry()) {
			if (e.getName().equals(prefix + ".xml"))
				return jar;
		}

		throw new IllegalArgumentException("did not contain xml");
	}

	@Override
	public OutputStream openOutputStream(Path repository, String prefix) throws IOException {
		OutputStream os = Files.newOutputStream(repository.resolve(prefix + ".jar"));
		JarOutputStream jar = new JarOutputStream(os);

		JarEntry je = new JarEntry(prefix + ".xml");

		jar.putNextEntry(je);

		if (true)
			return jar;

		return new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				jar.write(b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				jar.write(b, off, len);
			}

			@Override
			public void write(byte[] b) throws IOException {
				jar.write(b);
			}

			@Override
			public void flush() throws IOException {
				jar.flush();
			}

			@Override
			public void close() throws IOException {
				jar.close();
				os.close();
			}

		};

	}

}
