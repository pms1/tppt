package com.github.pms1.tppt.p2;

import java.io.InputStream;
import java.nio.file.Path;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = DataCompression.class, hint = "jar")
public class DataCompressionJar implements DataCompression {

	@Override
	public String getFileSuffix() {
		return "jar";
	}

	@Override
	public String getP2IndexSuffix() {
		return "xml";
	}

	@Override
	public InputStream openStream(Path path, String prefix) {
		throw new UnsupportedOperationException();
	}

}
