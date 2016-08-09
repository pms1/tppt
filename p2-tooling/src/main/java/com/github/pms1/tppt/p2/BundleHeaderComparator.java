package com.github.pms1.tppt.p2;

import java.util.function.Consumer;

public interface BundleHeaderComparator {

	boolean compare(FileId file1, FileId file2, String key, String v1, String v2, Consumer<FileDelta> dest);

}
