package com.github.pms1.tppt.p2;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.osgi.util.ManifestElement;

import com.google.common.collect.Sets;

@Component(role = BundleHeaderComparator.class, hint = BundleEclipseSourceBundleHeaderComparator.HINT)
public class BundleEclipseSourceBundleHeaderComparator extends AbstractManifestHeaderComparator {
	public final static String HINT = "Eclipse-SourceBundle";

	Map<String, List<String>> directives(ManifestElement e) {
		Enumeration<String> keys = e.getDirectiveKeys();
		if (keys == null)
			return Collections.emptyMap();
		Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
		for (; keys.hasMoreElements();) {
			String key = keys.nextElement();
			result.put(key, Arrays.asList(e.getDirectives(key)));
		}
		return result;
	}

	Map<String, List<String>> attributes(ManifestElement e) {
		Enumeration<String> keys = e.getKeys();
		if (keys == null)
			return Collections.emptyMap();
		Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
		for (; keys.hasMoreElements();) {
			String key = keys.nextElement();
			result.put(key, Arrays.asList(e.getAttributes(key)));
		}
		return result;
	}

	@Override
	public boolean compare(FileId file1, FileId file2, ManifestElement[] headers1, ManifestElement[] headers2,
			Consumer<FileDelta> dest) {

		if (headers1.length != 1)
			throw new Error();
		if (headers2.length != 1)
			throw new Error();

		if (!Objects.equals(headers1[0].getValue(), headers2[0].getValue()))
			return false;

		if (!Objects.equals(directives(headers1[0]), directives(headers2[0])))
			return false;

		Map<String, List<String>> a1 = attributes(headers1[0]);
		Map<String, List<String>> a2 = attributes(headers2[0]);

		List<String> v1 = null;
		List<String> v2 = null;
		for (String k : Sets.union(a1.keySet(), a2.keySet())) {
			switch (k) {
			case "version":
				v1 = a1.get(k);
				v2 = a2.get(k);
				break;
			default:
				return false;
			}
		}
		if (v1 == null || v1.size() != 1 || v2 == null || v2.size() != 1)
			return false;

		String baseline = v1.get(0);
		String current = v2.get(0);

		if (!Objects.equals(baseline, current))
			dest.accept(new ManifestEclipseSourceBundleVersionDelta(file1, file2,

					headers1[0].getValue(), VersionParser.valueOf(baseline), VersionParser.valueOf(current)));

		return true;
	}

}
