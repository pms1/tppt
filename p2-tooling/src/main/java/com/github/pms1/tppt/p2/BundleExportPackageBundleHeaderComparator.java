package com.github.pms1.tppt.p2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Version;

import com.github.pms1.tppt.p2.BundleManifestComparator.UnparseableManifestException;
import com.google.common.collect.Sets;

@Component(role = BundleHeaderComparator.class, hint = BundleExportPackageBundleHeaderComparator.HINT)
public class BundleExportPackageBundleHeaderComparator extends AbstractManifestHeaderComparator {
	public final static String HINT = "Export-Package";

	@Override
	public boolean compare(FileId file1, FileId file2, ManifestElement[] headers1, ManifestElement[] headers2,
			Consumer<FileDelta> dest) {

		Map<String, ManifestElement> package1 = new HashMap<>();
		for (ManifestElement e : headers1) {
			ManifestElement old = package1.put(e.getValue(), e);
			if (old != null)
				throw new UnparseableManifestException(file1, "Duplicate Export-Package " + e.getValue());
		}

		Map<String, ManifestElement> package2 = new HashMap<>();
		for (ManifestElement e : headers2) {
			ManifestElement old = package2.put(e.getValue(), e);
			if (old != null)
				throw new UnparseableManifestException(file1, "Duplicate Export-Package " + e.getValue());
		}

		for (String p : Sets.union(package1.keySet(), package2.keySet())) {
			ManifestElement e1 = package1.get(p);
			ManifestElement e2 = package2.get(p);
			if (e1 == null) {
				dest.accept(new ManifestExportPackageAddedDelta(file1, file2, p));
				continue;
			} else if (e2 == null) {
				dest.accept(new ManifestExportPackageRemovedDelta(file1, file2, p));
				continue;
			}

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
