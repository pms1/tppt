package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.codehaus.plexus.component.annotations.Component;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

@Component(hint = BinaryComparator.HINT, role = FileComparator.class)
public class BinaryComparator implements FileComparator {
	public static final String HINT = "binary";

	@Override
	public void compare(FileId file1, Path p1, FileId file2, Path p2, Consumer<FileDelta> dest) throws IOException {
		if (Files.size(p1) != Files.size(p2)) {
			dest.accept(new FileDelta(file1, file2, "Size changed"));
			return;
		}

		ByteSource bs1 = new PathByteSource(p1);
		ByteSource bs2 = new PathByteSource(p2);

		if (!bs1.contentEquals(bs2))
			dest.accept(new FileDelta(file1, file2, "Content changed"));
	}

	static class PathByteSource extends ByteSource {
		private Path path;

		PathByteSource(Path path) {
			Preconditions.checkNotNull(path);
			this.path = path;
		}

		@Override
		public long size() throws IOException {
			return Files.size(path);
		}

		@Override
		public byte[] read() throws IOException {
			return Files.readAllBytes(path);
		}

		@Override
		public InputStream openStream() throws IOException {
			return Files.newInputStream(path);
		}

	}
}
