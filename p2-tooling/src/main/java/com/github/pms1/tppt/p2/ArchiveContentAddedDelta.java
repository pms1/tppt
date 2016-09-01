package com.github.pms1.tppt.p2;

public class ArchiveContentAddedDelta extends FileDelta {

	ArchiveContentAddedDelta(FileId f1, FileId f2, String member) {
		super(f1, f2, "Member added: {0}", member);
	}

}
