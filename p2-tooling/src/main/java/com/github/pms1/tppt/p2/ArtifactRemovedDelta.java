package com.github.pms1.tppt.p2;

import com.google.common.base.Preconditions;

public class ArtifactRemovedDelta extends FileDelta {

	public ArtifactRemovedDelta(FileId baseline, FileId current, String p1, ArtifactId id) {
		super(baseline, current, "Artifact removed: {0}", p1);
		Preconditions.checkNotNull(id);
	}

}
