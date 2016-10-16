package com.github.pms1.tppt.p2;

import java.util.function.Consumer;

public interface BundleHeaderComparator {

	/**
	 * @param file1
	 * @param file2
	 * @param key
	 * @param v1
	 * @param v2
	 * @param dest
	 * @return If the comparator has handle the change
	 */
	boolean compare(FileId file1, FileId file2, String key, String v1, String v2, Consumer<FileDelta> dest);

}
