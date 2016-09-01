package com.github.pms1.tppt.p2;

import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

public class ManifestVersionDelta extends FileDelta {

	private final Version baseline;
	private final Version current;

	public ManifestVersionDelta(FileId f1, FileId f2, Version baseline, Version current) {
		super(f1, f2, "Manifest bundle version changed ''{0}'' -> ''{1}''", baseline, current);
		Preconditions.checkNotNull(baseline);
		this.baseline = baseline;
		Preconditions.checkNotNull(current);
		this.current = current;
	}

	public Version getBaselineVersion() {
		return baseline;
	}

	public Version getCurrentVersion() {
		return current;
	}

}
