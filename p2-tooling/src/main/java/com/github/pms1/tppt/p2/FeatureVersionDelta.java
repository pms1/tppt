package com.github.pms1.tppt.p2;

import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

public class FeatureVersionDelta extends FileDelta {
	private final Version baseline;
	private final Version current;

	public FeatureVersionDelta(FileId f1, FileId f2, String description, String featureId, Version baseline,
			Version current) {
		super(f1, f2, description);
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
