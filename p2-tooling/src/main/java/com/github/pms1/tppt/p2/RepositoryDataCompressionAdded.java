package com.github.pms1.tppt.p2;

public class RepositoryDataCompressionAdded extends FileDelta {

	public RepositoryDataCompressionAdded(FileId f1, FileId f2, DataCompression c) {
		super(f1, f2, "artifact.xml compression added: ." + c.getFileSuffix());
	}

}
