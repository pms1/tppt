package com.github.pms1.tppt.p2;

import com.google.common.base.Preconditions;

public class ArtifactClassifierDelta extends FileDelta {

	public ArtifactClassifierDelta(FileId baseline, FileId current, String artifactId, String baseline1,
			String current1) {
		super(baseline, current, "Artifact classifier changed: {0} -> {1}", baseline1, current1);
		Preconditions.checkNotNull(artifactId);
		Preconditions.checkArgument(!artifactId.isEmpty());
	}

}
