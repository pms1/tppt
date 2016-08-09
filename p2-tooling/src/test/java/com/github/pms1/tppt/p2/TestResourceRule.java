package com.github.pms1.tppt.p2;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class TestResourceRule implements TestRule {

	private Class<?> testClass;

	private List<Path> files = new LinkedList<>();

	@Override
	public Statement apply(Statement base, Description description) {
		description.getTestClass();
		return new Statement() {

			@Override
			public void evaluate() throws Throwable {
				if (testClass != null)
					throw new IllegalStateException();
				try {
					testClass = description.getTestClass();
					base.evaluate();
				} finally {
					for (Path p : files) {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					files.clear();
					testClass = null;
				}
			}

		};
	}

	Path getResource(String path) {
		if (testClass == null)
			throw new IllegalStateException();

		InputStream stream = testClass.getResourceAsStream(path);
		if (stream == null)
			throw new NoSuchElementException("No test resource '" + path + "' found");

		try {
			Path tempFile = Files.createTempFile(null, null);
			tempFile.toFile().deleteOnExit();
			files.add(tempFile);
			Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);

			return tempFile;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
