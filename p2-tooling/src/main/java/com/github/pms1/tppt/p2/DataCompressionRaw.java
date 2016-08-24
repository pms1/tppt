package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = DataCompression.class, hint = "xml")
public class DataCompressionRaw implements DataCompression {

	@Override
	public String getFileSuffix() {
		return "xml";
	}

	@Override
	public String getP2IndexSuffix() {
		return "xml";
	}

	@Override
	public InputStream openStream(Path path, String prefix) throws IOException {
		return Files.newInputStream(path.resolve(prefix + ".xml"));
	}

}
