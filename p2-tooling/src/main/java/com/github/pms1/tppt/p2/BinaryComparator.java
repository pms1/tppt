package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.codehaus.plexus.component.annotations.Component;

import com.google.common.io.ByteStreams;

@Component(hint = BinaryComparator.HINT, role = FileComparator.class)
public class BinaryComparator implements FileComparator {
	public static final String HINT = "binary";

	@Override
	public void compare(FileId file1, Path p1, FileId file2, Path p2, Consumer<FileDelta> dest) throws IOException {
		if (Files.size(p1) != Files.size(p2)) {
			dest.accept(new FileDelta(file1, file2, "Size changed"));
			return;
		}

		if (!ByteStreams.equal(() -> Files.newInputStream(p1), () -> Files.newInputStream(p2))) {
			dest.accept(new FileDelta(file1, file2, "Content changed"));
		}
	}

}
