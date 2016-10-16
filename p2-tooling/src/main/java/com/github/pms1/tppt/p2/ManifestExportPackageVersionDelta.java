package com.github.pms1.tppt.p2;

import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

public class ManifestExportPackageVersionDelta extends FileDelta {
	private final String pkg;
	private final Version v1;
	private final Version v2;

	public ManifestExportPackageVersionDelta(FileId f1, FileId f2, String pkg, Version v1, Version v2) {
		super(f1, f2, "Manifest export package version changed: ''{0}'': ''{1}'' -> ''{2}''", pkg, v1, v2);
		Preconditions.checkNotNull(pkg);
		this.pkg = pkg;
		this.v1 = v1;
		this.v2 = v2;
	}

	public Version getBaselineVersion() {
		return v1;
	}

	public Version getCurrentVersion() {
		return v2;
	}
}
