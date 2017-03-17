package com.github.pms1.tppt.p2;

import java.util.function.Consumer;

import com.github.pms1.tppt.p2.RepositoryComparator.Change;

public class RepositoryDataCompressionChange extends Change {
	@Override
	boolean accept(FileDelta delta) {
		// FIXME
		return delta instanceof RepositoryDataCompressionRemoved || delta instanceof RepositoryDataCompressionAdded
				|| delta instanceof RepositoryDataCompressionChanged;
	}

	@Override
	void check(Consumer<String> change) {
	}
}