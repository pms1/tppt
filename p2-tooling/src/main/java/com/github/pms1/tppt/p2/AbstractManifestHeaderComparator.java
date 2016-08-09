package com.github.pms1.tppt.p2;

import java.util.function.Consumer;

import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;

import com.github.pms1.tppt.p2.BundleManifestComparator.UnparseableManifestException;

public abstract class AbstractManifestHeaderComparator implements BundleHeaderComparator {

	@Override
	public final boolean compare(FileId file1, FileId file2, String key, String v1, String v2,
			Consumer<FileDelta> dest) {
		// TODO Auto-generated method stub
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

	protected abstract boolean compare(FileId file1, FileId file2, ManifestElement[] headers1,
			ManifestElement[] headers2, Consumer<FileDelta> dest);

}
