package com.github.pms1.tppt.p2;

import java.util.Objects;

import com.google.common.base.Preconditions;

public class ManifestHeaderDelta extends FileDelta {
	private final String key;

	public ManifestHeaderDelta(FileId f1, FileId f2, String key, String baselineValue, String currentValue) {
		super(f1, f2, "Manifest header changed: ''{0}'': ''{1}'' -> ''{2}''", key, baselineValue, currentValue);
		Preconditions.checkNotNull(key);
		Preconditions.checkArgument(!key.isEmpty());
		this.key = key;
		Objects.requireNonNull(baselineValue);
		Objects.requireNonNull(currentValue);
	}

	public String getKey() {
		return key;
	}
}
