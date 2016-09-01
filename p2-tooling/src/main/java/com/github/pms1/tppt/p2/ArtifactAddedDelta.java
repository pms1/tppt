package com.github.pms1.tppt.p2;

import com.google.common.base.Preconditions;

public class ArtifactAddedDelta extends FileDelta {

	public ArtifactAddedDelta(FileId baseline, FileId current, ArtifactId id, String value) {
		super(baseline, current, "Artifact added: {0}", value);
		Preconditions.checkNotNull(id);
	}

}
