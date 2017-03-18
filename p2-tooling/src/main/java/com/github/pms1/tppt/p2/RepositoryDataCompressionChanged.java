package com.github.pms1.tppt.p2;

import java.util.List;
import java.util.stream.Collectors;

import com.github.pms1.tppt.p2.P2RepositoryFactory.P2Kind;

public class RepositoryDataCompressionChanged extends FileDelta {

	public RepositoryDataCompressionChanged(FileId f1, FileId f2, P2Kind kind, List<DataCompression> c1,
			List<DataCompression> c2) {
		super(f1, f2, kind + " compression(s) changed: " + render(c1) + " -> " + render(c2));
	}

	private static String render(List<DataCompression> l) {
		return l.stream().map(DataCompression::getFileSuffix).collect(Collectors.joining(","));
	}
}
