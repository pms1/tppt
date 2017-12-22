package com.github.pms1.tppt.p2;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(hint = BundleComparator.HINT, role = FileComparator.class)
public class BundleComparator extends AbstractZipComparator {
	public static final String HINT = "bundle";

	@Requirement(hint = BundleManifestComparator.HINT)
	FileComparator bundleManifestComparator;

	@Requirement(hint = PropertiesComparator.HINT)
	FileComparator propertiesComparator;

	@Requirement(hint = ClassComparator.HINT)
	FileComparator classComparator;

	@Override
	protected FileComparator getComparator(String p) {
		if (p.equals("/META-INF/MANIFEST.MF")) {
			return bundleManifestComparator;
		} else if (p.endsWith(".properties")) {
			return propertiesComparator;
		} else if (p.endsWith(".class")) {
			return classComparator;
		} else {
			return null;
		}
	}

}
