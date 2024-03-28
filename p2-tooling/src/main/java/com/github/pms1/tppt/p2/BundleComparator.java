package com.github.pms1.tppt.p2;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named(BundleComparator.HINT)
@Singleton
public class BundleComparator extends AbstractZipComparator {
	public static final String HINT = "bundle";

	@Inject
	@Named(BundleManifestComparator.HINT)
	FileComparator bundleManifestComparator;

	@Inject
	@Named(PropertiesComparator.HINT)
	FileComparator propertiesComparator;

	@Inject
	@Named(ClassComparator.HINT)
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
