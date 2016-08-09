package com.github.pms1.tppt.p2;

import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

public class FeaturePluginVersionDelta extends FileDelta {

	private final String pluginId;
	private final Version baseline;
	private final Version current;

	public FeaturePluginVersionDelta(FileId f1, FileId f2, String description, String pluginId, Version baseline,
			Version current) {
		super(f1, f2, description);
		Preconditions.checkNotNull(pluginId);
		Preconditions.checkArgument(!pluginId.isEmpty());
		this.pluginId = pluginId;
		Preconditions.checkNotNull(baseline);
		this.baseline = baseline;
		Preconditions.checkNotNull(current);
		this.current = current;
	}

	public String getPluginId() {
		return pluginId;
	}

	public Version getBaselineVersion() {
		return baseline;
	}

	public Version getCurrentVersion() {
		return current;
	}

}
