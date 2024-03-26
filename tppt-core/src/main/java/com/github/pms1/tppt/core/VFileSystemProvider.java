package com.github.pms1.tppt.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.github.sardine.DavResource;
import com.github.sardine.impl.SardineException;
import com.google.common.collect.Iterables;

public class VFileSystemProvider extends FileSystemProvider {
	static VFileSystemProvider instance = new VFileSystemProvider();

	private static DavResource listSingle(VPath path) throws IOException {
		VPath vpath = (VPath) path;
		VFileSystem fs = vpath.getFileSystem();

		try {
			List<DavResource> list = fs.sadine.list(url(vpath, false).toURL().toString(), 0);
			if (list.isEmpty())
				throw new NoSuchFileException(vpath.toString());
			return Iterables.getOnlyElement(list);
		} catch (SardineException e) {
			processException(vpath, e);
			throw e;
		}
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {

		VPath vpath = (VPath) path;

		listSingle(vpath);

	}

	private static void processException(Path path, SardineException e) throws IOException {
		if (e.getStatusCode() == 404)
			throw new NoSuchFileException(path.toString());
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		throw new Error();

	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		VPath vpath = (VPath) dir;
		VFileSystem fs = vpath.getFileSystem();

		URI url = url(vpath, false);

		try {
			listSingle(vpath);
			throw new FileAlreadyExistsException("" + vpath);
		} catch (NoSuchFileException e) {
			// expected
		}

		if (vpath.getParent() != null) {
			DavResource parent = listSingle(vpath.getParent());
			if (!parent.isDirectory())
				throw new Error();
		}

		try {
			fs.sadine.createDirectory(url.toURL().toString());
		} catch (SardineException e) {
			System.err.println(e.getResponsePhrase());
			System.err.println(e.getStatusCode());
			throw e;
		}
	}

	@Override
	public void delete(Path path) throws IOException {

		VPath vpath = (VPath) path;
		VFileSystem fs = vpath.getFileSystem();

		fs.sadine.delete(url(vpath, false).toURL().toString());
	}

	static class BasicFileAttributeViewImpl implements BasicFileAttributeView {
		final VPath vpath;

		public BasicFileAttributeViewImpl(VPath vpath) {
			this.vpath = vpath;
		}

		@Override
		public String name() {
			throw new Error();
		}

		@Override
		public BasicFileAttributes readAttributes() throws IOException {
			throw new Error();
		}

		@Override
		public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
				throws IOException {
			listSingle(vpath);
			throw new Error();
		}

	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		VPath vpath = (VPath) path;

		if (type == BasicFileAttributeView.class)
			return type.cast(new BasicFileAttributeViewImpl(vpath));
		else
			throw new Error("type=" + type);
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		throw new Error();
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		throw new Error();
	}

	@Override
	public Path getPath(URI uri) {
		throw new Error();
	}

	@Override
	public String getScheme() {
		throw new Error();
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		throw new Error();
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		throw new Error();
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		throw new Error();

	}

	private static URI url(VPath vpath, boolean dir) throws MalformedURLException {
		URI uri = vpath.getFileSystem().root;

		for (Iterator<String> i = vpath.elements.iterator(); i.hasNext();) {
			String s = i.next();
			if (i.hasNext() || dir)
				s = s + "/";
			uri = uri.resolve(s);
		}

		return uri;
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		VPath vpath = (VPath) path;

		VFileSystem fs = vpath.getFileSystem();

		if (options.length == 0) {

			try {
				return new DelegateInputStream(fs.sadine.get(url(vpath, false).toURL().toString())) {
					boolean closed = false;

					@Override
					public void close() throws IOException {
						/*
						 * We sometimes do not read the file fully. Sardine issues a WARNING in the log.
						 * Avoid this by reading to the end always (at the risk of creating unneeded
						 * traffic).
						 */
						if (!closed) {
							byte[] buffer = new byte[8192];
							while (read(buffer) != -1)
								;
							closed = true;
						}

						super.close();
					}
				};
			} catch (SardineException e) {
				processException(path, e);
				throw e;
			}

		} else {
			throw new Error(Arrays.toString(options));
		}

	}

	@Override
	public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {

		VPath vpath = (VPath) path;

		VFileSystem fs = vpath.getFileSystem();

		return new ByteArrayOutputStream() {
			public void close() throws IOException {
				super.close();

				fs.sadine.put(url(vpath, false).toURL().toString(), toByteArray(), "application/octet-stream");
			};

		};
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		throw new Error("" + options);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		VPath vpath = (VPath) dir;

		VFileSystem fs = vpath.getFileSystem();

		URI root = url(vpath, true);

		List<DavResource> list;
		try {
			list = fs.sadine.list(root.toURL().toString(), 1, Collections.emptySet());
			if (list.isEmpty())
				throw new NoSuchFileException("" + vpath);
		} catch (SardineException e) {
			processException(vpath, e);
			throw e;
		}

		List<Path> result = new ArrayList<>(list.size() - 1);
		for (DavResource r : list.subList(1, list.size())) {
			if (!r.getPath().startsWith(root.getPath()))
				throw new Error("" + r.getPath() + " " + root.getPath());

			String rel = r.getPath().substring(root.getPath().length());
			if (rel.isEmpty())
				throw new Error();

			// apache: has trailing / for directories
			if (rel.endsWith("/"))
				rel = rel.substring(0, rel.length() - 1);

			if (rel.contains("/"))
				throw new Error(r + " " + r.getPath() + " " + rel);

			/* artifactory: provides .sha256 for some files, but they are not accessible */
			if (Objects.equals(r.getContentType(), "application/x-checksum"))
				continue;

			Path p = dir.resolve(rel);
			if (filter.accept(p))
				result.add(p);
		}

		return new DirectoryStream<Path>() {

			@Override
			public void close() throws IOException {
			}

			@Override
			public Iterator<Path> iterator() {
				return result.iterator();
			}

		};
	}

	static class DFS extends FileSystem {
		final FileSystem delegate;

		DFS(FileSystem delegate) {
			this.delegate = delegate;
		}

		public int hashCode() {
			return delegate.hashCode();
		}

		public boolean equals(Object obj) {
			return delegate.equals(obj);
		}

		public FileSystemProvider provider() {
			return delegate.provider();
		}

		public void close() throws IOException {
			delegate.close();
		}

		public boolean isOpen() {
			return delegate.isOpen();
		}

		public boolean isReadOnly() {
			return delegate.isReadOnly();
		}

		public String getSeparator() {
			return delegate.getSeparator();
		}

		public Iterable<Path> getRootDirectories() {
			return delegate.getRootDirectories();
		}

		public Iterable<FileStore> getFileStores() {
			return delegate.getFileStores();
		}

		public String toString() {
			return delegate.toString();
		}

		public Set<String> supportedFileAttributeViews() {
			return delegate.supportedFileAttributeViews();
		}

		public Path getPath(String first, String... more) {
			return delegate.getPath(first, more);
		}

		public PathMatcher getPathMatcher(String syntaxAndPattern) {
			return delegate.getPathMatcher(syntaxAndPattern);
		}

		public UserPrincipalLookupService getUserPrincipalLookupService() {
			return delegate.getUserPrincipalLookupService();
		}

		public WatchService newWatchService() throws IOException {
			return delegate.newWatchService();
		}

	}

	@Override
	public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
		VPath vpath = (VPath) path;

		Path temp = Files.createTempFile(null, null);
		temp.toFile().deleteOnExit();
		try (InputStream is = newInputStream(path)) {
			Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
		}

		return new DFS(FileSystems.newFileSystem(temp, (ClassLoader) null)) {
			@Override
			public void close() throws IOException {
				try {
					super.close();
				} finally {
					Files.delete(temp);
				}
			}

		};
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		throw new Error();
	}

	static class BasicFileAttributesImpl implements BasicFileAttributes {
		final DavResource dr;

		public BasicFileAttributesImpl(DavResource dr) {
			this.dr = dr;
		}

		@Override
		public FileTime creationTime() {
			throw new Error();
		}

		@Override
		public Object fileKey() {
			// revisit later
			return null;
		}

		@Override
		public boolean isDirectory() {
			return dr.isDirectory();
		}

		@Override
		public boolean isOther() {
			throw new Error();
		}

		@Override
		public boolean isRegularFile() {
			throw new Error();
		}

		@Override
		public boolean isSymbolicLink() {
			return false;
		}

		@Override
		public FileTime lastAccessTime() {
			throw new Error();
		}

		@Override
		public FileTime lastModifiedTime() {
			throw new Error();
		}

		@Override
		public long size() {
			return dr.getContentLength();
		}

	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		if (type == BasicFileAttributes.class) {
			VPath vpath = (VPath) path;

			VFileSystem fs = vpath.getFileSystem();

			List<DavResource> list = fs.sadine.list(fileUrl(vpath), 0);
			if (list.isEmpty())
				throw new NoSuchFileException("" + vpath);

			if (list.size() != 1)
				throw new Error("" + list);

			return type.cast(new BasicFileAttributesImpl(Iterables.getOnlyElement(list)));
		} else {
			throw new Error();
		}
	}

	private String fileUrl(VPath vpath) throws MalformedURLException {
		return url(vpath, false).toURL().toString();
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		throw new Error();
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		throw new Error();

	}

}
