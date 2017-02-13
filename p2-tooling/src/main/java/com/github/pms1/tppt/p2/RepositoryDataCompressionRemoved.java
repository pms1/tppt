package com.github.pms1.tppt.p2;

public class RepositoryDataCompressionRemoved extends FileDelta {

	public RepositoryDataCompressionRemoved(FileId f1, FileId f2, DataCompression c) {
		super(f1, f2, "artifact.xml compression removed: ." + c.getFileSuffix());
	}

}
