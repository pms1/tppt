package com.github.pms1.tppt.p2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Version;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

@Named(BundleExportPackageBundleHeaderComparator.HINT)
@Singleton
public class BundleExportPackageBundleHeaderComparator extends AbstractManifestHeaderComparator {
	public final static String HINT = "Export-Package";

	Multimap<String, ManifestElement> toMultimap(ManifestElement[] headers) {
		Multimap<String, ManifestElement> result = HashMultimap.create();
		for (ManifestElement element : headers)
			result.put(element.getValue(), element);
		return result;
	}

	@Override
	public boolean compare(FileId file1, FileId file2, ManifestElement[] headers1, ManifestElement[] headers2,
			Consumer<FileDelta> dest) {

		Multimap<String, ManifestElement> package1 = toMultimap(headers1);
		Multimap<String, ManifestElement> package2 = toMultimap(headers2);

		for (String p : Sets.union(package1.keySet(), package2.keySet())) {

			Collection<ManifestElement> entries1 = new ArrayList<>(package1.get(p));
			Collection<ManifestElement> entries2 = new ArrayList<>(package2.get(p));

			// remove common entries
			for (Iterator<ManifestElement> i1 = entries1.iterator(); i1.hasNext();) {
				ManifestElement e1 = i1.next();
				boolean remove = false;
				for (Iterator<ManifestElement> i2 = entries2.iterator(); i2.hasNext();) {
					ManifestElement e2 = i2.next();
					if (compare(file1, e1, file2, e2)) {
						i2.remove();
						remove = true;
					}
				}
				if (remove)
					i1.remove();
			}

			if (entries2.size() > entries1.size()) {
				dest.accept(new ManifestExportPackageAddedDelta(file1, file2, p));
				continue;
			} else if (entries2.size() < entries1.size()) {
				dest.accept(new ManifestExportPackageRemovedDelta(file1, file2, p));
				continue;
			} else if (entries1.isEmpty()) {
				continue;
			} else if (entries1.size() != 1) {
				return false;
			}

			ManifestElement e1 = Iterables.getOnlyElement(entries1);
			ManifestElement e2 = Iterables.getOnlyElement(entries2);

			if (!Objects.equals(directives(file1, e1), directives(file2, e2)))
				return false;

			Map<String, List<String>> attributes1 = attributes(file1, e1);
			Map<String, List<String>> attributes2 = attributes(file2, e2);

			for (String a : Sets.union(attributes1.keySet(), attributes2.keySet())) {
				switch (a) {
				case "version":
					List<String> v1 = attributes1.get(a);
					List<String> v2 = attributes2.get(a);
					if (v1.size() > 1)
						return false;
					Version vv1 = v1.isEmpty() ? null : Version.parseVersion(v1.get(0));
					if (v2.size() > 1)
						return false;
					Version vv2 = v2.isEmpty() ? null : Version.parseVersion(v2.get(0));

					if (!Objects.equals(v1, v2))
						dest.accept(new ManifestExportPackageVersionDelta(file1, file2, p, vv1, vv2));

					break;
				default:
					return false;
				}
			}
		}

		return true;
	}

}
