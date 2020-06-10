package com.github.pms1.tppt.core;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.google.common.base.Objects;

class VPath implements Path {

	private final VFileSystem fs;
	final List<String> elements;
	private final boolean absolute;

	VPath(VFileSystem fs) {
		this.fs = fs;
		this.elements = Collections.emptyList();
		this.absolute = true;
	}

	VPath(VFileSystem fs, List<String> elements, boolean absolute) {
		this.fs = fs;
		this.elements = elements;
		this.absolute = absolute;

		if (elements.stream().anyMatch(x -> x.contains("/")))
			throw new Error();
	}

	@Override
	public int compareTo(Path arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean endsWith(Path arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean endsWith(String arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Path getFileName() {
		throw new UnsupportedOperationException();
	}

	@Override
	public VFileSystem getFileSystem() {
		return fs;
	}

	@Override
	public Path getName(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getNameCount() {
		throw new UnsupportedOperationException();
	}

	@Override
	public VPath getParent() {
		if (elements.isEmpty())
			return null;
		else
			return new VPath(fs, elements.subList(0, elements.size() - 1), absolute);
	}

	@Override
	public Path getRoot() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAbsolute() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Path> iterator() {
		return elements.stream().map(p -> (Path) new VPath(fs, Arrays.asList(p), false)).iterator();
	}

	@Override
	public Path normalize() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Path relativize(Path other) {
		VPath vother = (VPath) other;

		ListIterator<String> t = elements.listIterator();
		ListIterator<String> o = vother.elements.listIterator();

		while (t.hasNext() && o.hasNext()) {
			String t1 = t.next();
			String o2 = o.next();
			if (!Objects.equal(t1, o2)) {
				t.previous();
				o.previous();
				break;
			}
		}

		if (!t.hasNext()) {
			return new VPath(fs, vother.elements.subList(o.nextIndex(), vother.elements.size()), false);
		} else {
			throw new UnsupportedOperationException(t + " " + o);
		}
	}

	@Override
	public Path resolve(Path other) {
		VPath vother = (VPath) other;

		if (vother.absolute) {
			return vother;
		}

		List<String> copy = new ArrayList<>(elements.size() + vother.elements.size());
		copy.addAll(elements);
		copy.addAll(vother.elements);
		return new VPath(fs, copy, absolute);
	}

	@Override
	public Path resolve(String other) {
		String[] split = other.split("/", -1);
		if (other.startsWith("/"))
			throw new Error();

		List<String> copy = new ArrayList<>(elements.size() + split.length);
		copy.addAll(elements);
		Collections.addAll(copy, split);
		return new VPath(fs, copy, absolute);
	}

	@Override
	public Path resolveSibling(Path other) {
		if (elements.isEmpty())
			return other;
		return getParent().resolve(other);
	}

	@Override
	public Path resolveSibling(String other) {
		if (elements.isEmpty())
			return fs.getPath(other);
		return getParent().resolve(other);
	}

	@Override
	public boolean startsWith(Path other) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean startsWith(String other) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Path subpath(int beginIndex, int endIndex) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Path toAbsolutePath() {
		if (absolute)
			return this;

		throw new UnsupportedOperationException();
	}

	@Override
	public File toFile() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public URI toUri() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		if (absolute)
			return "[" + fs.root + "," + String.join("/", elements);
		else
			return String.join("/", elements);
	}

}
