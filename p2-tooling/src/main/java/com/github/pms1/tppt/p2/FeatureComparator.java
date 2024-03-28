package com.github.pms1.tppt.p2;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named(FeatureComparator.HINT)
@Singleton
public class FeatureComparator extends AbstractZipComparator {
	final static public String HINT = "feature-jar";

	@Inject
	@Named("feature.xml")
	private FileComparator featureXmlComparator;

	@Override
	protected FileComparator getComparator(String p) {
		if (p.equals("/feature.xml")) {
			return featureXmlComparator;
		} else {
			return null;
		}
	}

}
