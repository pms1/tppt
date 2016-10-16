package com.github.pms1.tppt.p2;

import com.google.common.base.Preconditions;

public class ManifestExportPackageRemovedDelta extends FileDelta {
	private final String pkg;

	public ManifestExportPackageRemovedDelta(FileId f1, FileId f2, String pkg) {
		super(f1, f2, "Manifest export package removed: ''{0}''", pkg);
		Preconditions.checkNotNull(pkg);
		this.pkg = pkg;
	}
}
