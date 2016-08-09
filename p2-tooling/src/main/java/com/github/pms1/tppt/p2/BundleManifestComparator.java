package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;

import com.google.common.collect.Sets;

@Component(role = FileComparator.class, hint = BundleManifestComparator.HINT)
public class BundleManifestComparator implements FileComparator {
	public final static String HINT = "bundle-manifest";

	static class UnparseableManifestException extends SemanticException {

		public UnparseableManifestException(FileId file1, String text, BundleException e) {
			super(file1, text, e);
		}

		public UnparseableManifestException(FileId file1, String text) {
			super(file1, text);
		}

	}

	@Requirement
	private Map<String, BundleHeaderComparator> comparators;

	@Override
	public void compare(FileId file1, Path p1, FileId file2, Path p2, Consumer<FileDelta> dest) throws IOException {

		Map<String, String> m1;
		try (InputStream is = Files.newInputStream(p1)) {
			m1 = ManifestElement.parseBundleManifest(is, null);
		} catch (BundleException e) {
			throw new UnparseableManifestException(file1, "Failed to parse manifest", e);
		}

		Map<String, String> m2;
		try (InputStream is = Files.newInputStream(p2)) {
			m2 = ManifestElement.parseBundleManifest(is, null);
		} catch (BundleException e) {
			throw new UnparseableManifestException(file2, "Failed to parse manifest", e);
		}

		for (String key : Sets.union(m1.keySet(), m2.keySet())) {
			String v1 = m1.get(key);
			if (v1 == null) {
				dest.accept(new FileDelta(file1, file2, "Manifest header '" + key + "' added"));
				continue;
			}

			String v2 = m2.get(key);
			if (v2 == null) {
				dest.accept(new FileDelta(file1, file2, "Manifest header '" + key + "' removed"));
				continue;
			}

			if (Objects.equals(v1, v2))
				continue;

			BundleHeaderComparator comparator = comparators.get(key);
			if (comparator != null)
				if (comparator.compare(file1, file2, key, v1, v2, dest))
					continue;

			dest.accept(new ManifestHeaderDelta(file1, file2, "Manifest header '" + key + "' changed '" + v1 + "' -> '" + v2 + "'", key, v1,
					v2));
		}
	}

}
