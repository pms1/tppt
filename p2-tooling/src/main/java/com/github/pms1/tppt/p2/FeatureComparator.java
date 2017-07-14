package com.github.pms1.tppt.p2;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(hint = FeatureComparator.HINT, role = FileComparator.class)
public class FeatureComparator extends AbstractZipComparator {
	final static public String HINT = "feature-jar";

	@Requirement(hint = "feature.xml")
	FileComparator featureXmlComparator;

	@Override
	protected FileComparator getComparator(String p) {
		if (p.equals("/feature.xml")) {
			return featureXmlComparator;
		} else {
			return null;
		}
	}

}
