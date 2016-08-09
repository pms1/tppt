package com.github.pms1.tppt.p2;

import com.google.common.base.Preconditions;

public class ArtifactAddedDelta extends FileDelta {

	public ArtifactAddedDelta(FileId baseline, FileId current, String description, ArtifactId id) {
		super(baseline, current, description);
		Preconditions.checkNotNull(id);
	}

}
