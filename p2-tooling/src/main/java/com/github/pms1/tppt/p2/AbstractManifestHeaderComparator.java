package com.github.pms1.tppt.p2;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;

import com.github.pms1.tppt.p2.BundleManifestComparator.UnparseableManifestException;

public abstract class AbstractManifestHeaderComparator implements BundleHeaderComparator {

	@Override
	public final boolean compare(FileId file1, FileId file2, String key, String v1, String v2,
			Consumer<FileDelta> dest) {
		ManifestElement[] headers1;
		try {
			headers1 = ManifestElement.parseHeader(key, v1);
		} catch (BundleException e) {
			throw new UnparseableManifestException(file1, "Failed to parse manifest " + key + " " + v1, e);
		}

		ManifestElement[] headers2;
		try {
			headers2 = ManifestElement.parseHeader(key, v2);
		} catch (BundleException e) {
			throw new UnparseableManifestException(file1, "Failed to parse manifest " + key + " " + v1, e);
		}

		return compare(file1, file2, headers1, headers2, dest);
	}

	protected Map<String, List<String>> directives(FileId id, ManifestElement e) {
		Enumeration<String> keys = e.getDirectiveKeys();
		if (keys == null)
			return Collections.emptyMap();
		Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
		for (; keys.hasMoreElements();) {
			String key = keys.nextElement();
			Object old = result.putIfAbsent(key, Arrays.asList(e.getDirectives(key)));
			if (old != null)
				throw new UnparseableManifestException(id, "Duplicate directive " + key + " " + e);
		}
		return result;
	}

	protected Map<String, List<String>> attributes(FileId id, ManifestElement e) {
		Enumeration<String> keys = e.getKeys();
		if (keys == null)
			return Collections.emptyMap();
		Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
		for (; keys.hasMoreElements();) {
			String key = keys.nextElement();
			Object old = result.putIfAbsent(key, Arrays.asList(e.getAttributes(key)));
			if (old != null)
				throw new UnparseableManifestException(id, "Duplicate attribute " + key + " " + e);
		}
		return result;
	}

	protected boolean compare(FileId id1, ManifestElement e1, FileId id2, ManifestElement e2) {
		if (!Objects.equals(e1.getValue(), e2.getValue()))
			return false;
		if (!Objects.equals(attributes(id1, e1), attributes(id2, e2)))
			return false;
		if (!Objects.equals(directives(id1, e1), directives(id2, e2)))
			return false;
		return true;
	}

	protected abstract boolean compare(FileId file1, FileId file2, ManifestElement[] headers1,
			ManifestElement[] headers2, Consumer<FileDelta> dest);

}
