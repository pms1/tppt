package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.io.ByteSource;

@Named(BinaryComparator.HINT)
@Singleton
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
}
