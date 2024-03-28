package com.github.pms1.tppt.p2;

import java.util.Objects;
import java.util.function.Consumer;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.osgi.util.ManifestElement;

import com.github.pms1.tppt.p2.BundleManifestComparator.UnparseableManifestException;

@Named(BundleVersionHeaderComparator.HINT)
@Singleton
public class BundleVersionHeaderComparator extends AbstractManifestHeaderComparator {
	// if the constant is used, turns up as "default" in Plexus! The bytecode is
	// ok, but not the components.xml
	// so this might be a bug in the plexus annotation processor
	public final static String HINT = "Bundle-Version"; // Constants.BUNDLE_VERSION

	@Override
	public boolean compare(FileId file1, FileId file2, ManifestElement[] headers1, ManifestElement[] headers2,
			Consumer<FileDelta> dest) {

		if (headers1.length != 1)
			throw new UnparseableManifestException(file1, "foo");
		if (headers2.length != 1)
			throw new UnparseableManifestException(file2, "foo");
		if (headers1[0].getDirectiveKeys() != null)
			throw new UnparseableManifestException(file1, "foo");
		if (headers2[0].getDirectiveKeys() != null)
			throw new UnparseableManifestException(file2, "foo");

		if (headers1[0].getKeys() != null)
			throw new UnparseableManifestException(file1, "foo");
		if (headers2[0].getKeys() != null)
			throw new UnparseableManifestException(file2, "foo");

		if (!Objects.equals(headers1[0].getValue(), headers2[0].getValue()))
			dest.accept(new ManifestVersionDelta(file1, file2,

					VersionParser.valueOf(headers1[0].getValue()), VersionParser.valueOf(headers2[0].getValue())));

		return true;
	}

}
