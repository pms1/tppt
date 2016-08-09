package com.github.pms1.tppt.p2;

import com.google.common.base.Preconditions;

public class ArtifactRemovedDelta extends FileDelta {

	public ArtifactRemovedDelta(FileId baseline, FileId current, String description, ArtifactId id) {
		super(baseline, current, description);
		Preconditions.checkNotNull(id);
	}

}
