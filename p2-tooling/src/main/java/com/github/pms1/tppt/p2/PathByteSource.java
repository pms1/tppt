package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

public class PathByteSource extends ByteSource {
	private Path path;

	public PathByteSource(Path path) {
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