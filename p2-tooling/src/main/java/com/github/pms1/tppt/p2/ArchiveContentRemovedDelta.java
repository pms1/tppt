package com.github.pms1.tppt.p2;

public class ArchiveContentRemovedDelta extends FileDelta {

	ArchiveContentRemovedDelta(FileId f1, FileId f2, String member) {
		super(f1, f2, "Member removed: {0}", member);
	}

}
