package com.github.pms1.tppt.p2;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.tycho.artifactcomparator.ArtifactComparator;
import org.eclipse.tycho.zipcomparator.internal.ZipComparatorImpl;

@Component(hint = BundleComparator.HINT, role = FileComparator.class)
public class BundleComparator extends AbstractZipComparator {
	public static final String HINT = "bundle";

	@Requirement(hint = ZipComparatorImpl.TYPE)
	ArtifactComparator tychoComparator;
	//
	// @Override
	// public void compare(FileId file1, Path p1, FileId file2, Path p2,
	// Consumer<FileDelta> dest) throws IOException {
	// ArtifactDelta delta = tychoComparator.getDelta(p1.toFile(), p2.toFile());
	//
	// System.err.println("TC " + p1 + " " + p2 + " " + delta);
	// if (delta != null)
	// dest.accept(new FileDelta(file1, file2, delta.getDetailedMessage()));
	// }

	@Requirement(hint = BundleManifestComparator.HINT)
	FileComparator bundleManifestComparator;

	@Requirement(hint = PropertiesComparator.HINT)
	FileComparator propertiesComparator;

	@Override
	protected FileComparator getComparator(String p) {
		if (p.equals("/META-INF/MANIFEST.MF")) {
			return bundleManifestComparator;
		} else if (p.endsWith(".properties")) {
			return propertiesComparator;
		} else {
			return null;
		}
	}

}
