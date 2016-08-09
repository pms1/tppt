package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

import javax.xml.bind.JAXB;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = ArtifactRepositoryFactory.class)
public class ArtifactRepositoryFactory {

	public ArtifactRepository read(Path p) throws IOException {

		Properties p2index = new Properties();

		try (InputStream is = Files.newInputStream(p.resolve("p2.index"))) {
			p2index.load(is);
		}

		if (!Objects.equals(p2index.getProperty("version", null), "1"))
			throw new Error();

		ArtifactRepositoryData data = null;
		for (String f : p2index.getProperty("artifact.repository.factory.order", "").split(",")) {
			switch (f) {
			case "artifacts.xml":
				try (InputStream is = Files.newInputStream(p.resolve("artifacts.xml"))) {
					data = JAXB.unmarshal(is, ArtifactRepositoryData.class);
				}
				break;
			case "!":
				throw new Error();
			default:
				throw new Error("f=" + f);
			}

			if (data != null)
				break;
		}

		return new ArtifactRepositoryImpl(p.toUri(), data);
	}
}
