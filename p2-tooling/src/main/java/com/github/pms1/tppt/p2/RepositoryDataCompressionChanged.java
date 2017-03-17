package com.github.pms1.tppt.p2;

public class RepositoryDataCompressionChanged extends FileDelta {

	public RepositoryDataCompressionChanged(FileId f1, FileId f2, DataCompression c1, DataCompression c2) {
		super(f1, f2, "artifact.xml compression changed: ." + c1.getFileSuffix() + " -> " + c2.getFileSuffix());
	}

}
