package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public interface DataCompression {

	String getFileSuffix();

	String getP2IndexSuffix();

	InputStream openInputStream(Path repository, String prefix) throws IOException;

	OutputStream openOutputStream(Path repository, String prefix) throws IOException;
}
