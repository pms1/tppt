package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface DataCompression {

	String getFileSuffix();

	String getP2IndexSuffix();

	InputStream openStream(Path path, String prefix) throws IOException;
}
