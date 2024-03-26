package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;

public class NestedZipFileSystemProviderTest {

	@Rule
	public TestResourceRule r = new TestResourceRule();

	@Test
	public void t1() throws IOException {
		Path p = r.getResource("testtest.zip");

		try (FileSystem fs = FileSystems.newFileSystem(p, (ClassLoader) null)) {
			Path p1 = fs.getPath("/test.zip");
			try (FileSystem fs2 = FileSystems.newFileSystem(p1, (ClassLoader) null)) {
				Path p2 = fs2.getPath("/test.txt");

				Assertions.assertThat(Files.exists(p2)).isEqualTo(true);
				Assertions.assertThat(Files.size(p2)).isEqualTo(0);
			}
		}
	}
}
