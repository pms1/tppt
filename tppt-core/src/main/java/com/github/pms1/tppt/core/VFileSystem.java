package com.github.pms1.tppt.core;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.github.sardine.Sardine;

class VFileSystem extends FileSystem {
	final URI root;
	Sardine sadine;

	VFileSystem(Sardine sardine, URI root) {
		this.sadine = sardine;
		this.root = root;
	}

	@Override
	public void close() throws IOException {

	}

	@Override
	public Iterable<FileStore> getFileStores() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Path getPath(String first, String... more) {

		/*
		 * Mimic Paths.getPath() ignores leading and trailing / and folds multiple /
		 * into one without creating empty path elements. Do the same.
		 * 
		 * Not sure about the leading on Unix however.
		 */

		List<String> elements = new ArrayList<>();

		Collections.addAll(elements, first.split("/+", -1));
		for (String m : more)
			Collections.addAll(elements, m.split("/+", -1));

		elements.removeIf(String::isEmpty);

		return new VPath(this, elements, false);
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getSeparator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isOpen() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isReadOnly() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public FileSystemProvider provider() {
		return VFileSystemProvider.instance;
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		throw new UnsupportedOperationException();
	}
}
