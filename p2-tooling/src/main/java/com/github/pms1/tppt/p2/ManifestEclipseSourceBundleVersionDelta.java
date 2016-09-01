package com.github.pms1.tppt.p2;

import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

public class ManifestEclipseSourceBundleVersionDelta extends FileDelta {
	private final String bundleId;

	private final Version baseline;
	private final Version current;

	public ManifestEclipseSourceBundleVersionDelta(FileId f1, FileId f2, String bundleId, Version baseline,
			Version current) {
		super(f1, f2, "Manifest eclipse source bundle version changed: ''{0}'' -> ''{1}''", baseline, current);
		Preconditions.checkNotNull(bundleId);
		Preconditions.checkArgument(!bundleId.isEmpty());
		this.bundleId = bundleId;
		Preconditions.checkNotNull(baseline);
		this.baseline = baseline;
		Preconditions.checkNotNull(current);
		this.current = current;
	}

	public String getBundleId() {
		return bundleId;
	}

	public Version getBaselineVersion() {
		return baseline;
	}

	public Version getCurrentVersion() {
		return current;
	}

}
