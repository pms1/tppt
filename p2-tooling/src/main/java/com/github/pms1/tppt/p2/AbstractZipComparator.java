package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipError;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.IOUtil;

import com.google.common.collect.Sets;

abstract public class AbstractZipComparator implements FileComparator {

	@Requirement(hint = BinaryComparator.HINT)
	private FileComparator binaryComparator;

	@Override
	public final void compare(FileId file1, Path p1, FileId file2, Path p2, Consumer<FileDelta> dest)
			throws IOException {

		try (FileSystem fs1 = FileSystems.newFileSystem(p1, getClass().getClassLoader())) {
			Map<String, Path> s1 = asMap(file1, fs1);

			try (FileSystem fs2 = FileSystems.newFileSystem(p2, getClass().getClassLoader())) {
				Map<String, Path> s2 = asMap(file2, fs2);

				runComparison(file1, file2, dest, s1, s2);

				return;
			} catch (NoSuchFileException e) {
			} catch (ZipError e) {
				throw new UnparseableZipException(file2, e);
			}
		} catch (NoSuchFileException e) {

		} catch (ZipError e) {
			throw new UnparseableZipException(file1, e);
		}

		Path p1copy = copy(p1);
		try {
			Path p2copy = copy(p2);
			try {

				try (FileSystem fs1 = FileSystems.newFileSystem(p1copy, getClass().getClassLoader())) {
					Map<String, Path> s1 = asMap(file1, fs1);

					try (FileSystem fs2 = FileSystems.newFileSystem(p2copy, getClass().getClassLoader())) {
						Map<String, Path> s2 = asMap(file2, fs2);

						runComparison(file1, file2, dest, s1, s2);
					} catch (ZipError e) {
						throw new UnparseableZipException(file2, e);
					}
				} catch (ZipError e) {
					throw new UnparseableZipException(file1, e);
				}

			} finally {
				try {
					Files.deleteIfExists(p2copy);
				} catch (IOException e) {

				}
			}
		} finally {
			try {
				Files.deleteIfExists(p1copy);
			} catch (IOException e) {

			}
		}
	}

	Path copy(Path p) throws IOException {
		Path copy = Files.createTempFile(null, null);

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(p))) {
			try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(copy))) {

				ZipEntry ze;

				while ((ze = zis.getNextEntry()) != null) {

					String name = ze.getName();
					while (name.startsWith("/"))
						name = name.substring(1);

					ZipEntry e2 = new ZipEntry(name);
					e2.setComment(ze.getComment());
					if (ze.getCreationTime() != null)
						e2.setCreationTime(ze.getCreationTime());
					e2.setExtra(ze.getExtra());
					if (ze.getLastAccessTime() != null)
						e2.setLastAccessTime(ze.getLastAccessTime());
					if (ze.getLastModifiedTime() != null)
						e2.setLastModifiedTime(ze.getLastModifiedTime());

					zos.putNextEntry(e2);
					IOUtil.copy(zis, zos);
				}
			}
		}

		return copy;
	}

	void runComparison(FileId file1, FileId file2, Consumer<FileDelta> dest, Map<String, Path> s1, Map<String, Path> s2)
			throws IOException {
		for (String p : Sets.union(s1.keySet(), s2.keySet())) {
			Path pc1 = s1.get(p);
			if (pc1 == null) {
				dest.accept(new ArchiveContentAddedDelta(file1, file2, p));
				continue;
			}
			Path pc2 = s2.get(p);
			if (pc2 == null) {
				dest.accept(new ArchiveContentRemovedDelta(file1, file2, p));
				continue;
			}

			FileId c1 = FileId.newChild(file1, pc1);
			FileId c2 = FileId.newChild(file2, pc2);
			List<FileDelta> binaryDelta = new ArrayList<>();
			binaryComparator.compare(c1, pc1, c2, pc2, binaryDelta::add);

			if (binaryDelta.isEmpty())
				continue;

			FileComparator comparator = getComparator(p);
			if (comparator == null) {
				binaryDelta.forEach(dest);
				continue;
			}

			comparator.compare(c1, pc1, c2, pc2, dest);
		}
	}

	abstract protected FileComparator getComparator(String p);

	static class UnparseableZipException extends SemanticException {

		public UnparseableZipException(FileId file, Throwable parent) {
			super(file, parent.toString(), parent);
		}

	}

	private Map<String, Path> asMap(FileId fileId, FileSystem fs1) throws IOException {
		try {
			Map<String, Path> result = new HashMap<>();
			Files.walkFileTree(fs1.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					result.put(file.toString(), file);
					return super.visitFile(file, attrs);
				}
			});

			return result;
		} catch (ArrayIndexOutOfBoundsException e) {
			// seen on some IBM Jars, e.g.
			// com.ibm.help.common.accessibility.doc_7.6.0.v20130521_2201.jar
			// cygwin's unzip also reports errors on those.
			throw new UnparseableZipException(fileId, e);
		}
	}

}
