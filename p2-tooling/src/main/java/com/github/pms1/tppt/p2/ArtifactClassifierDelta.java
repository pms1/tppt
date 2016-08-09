package com.github.pms1.tppt.p2;

import com.google.common.base.Preconditions;

public class ArtifactClassifierDelta extends FileDelta {

	public ArtifactClassifierDelta(FileId baseline, FileId current, String description, String artifactId) {
		super(baseline, current, description);
		Preconditions.checkNotNull(artifactId);
		Preconditions.checkArgument(!artifactId.isEmpty());
	}

}
