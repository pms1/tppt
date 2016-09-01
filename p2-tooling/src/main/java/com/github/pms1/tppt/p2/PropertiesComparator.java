package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

import org.codehaus.plexus.component.annotations.Component;

import com.google.common.collect.Sets;

@Component(role = FileComparator.class, hint = PropertiesComparator.HINT)
public class PropertiesComparator implements FileComparator {
	public final static String HINT = "properties";

	@Override
	public void compare(FileId file1, Path p1, FileId file2, Path p2, Consumer<FileDelta> dest) throws IOException {
		Properties prop1 = new Properties();
		try (InputStream is = Files.newInputStream(p1)) {
			prop1.load(is);
		}

		Properties prop2 = new Properties();
		try (InputStream is = Files.newInputStream(p2)) {
			prop2.load(is);
		}

		for (Object k : Sets.union(prop1.keySet(), prop2.keySet())) {
			Object v1 = prop1.get(k);
			if (v1 == null) {
				dest.accept(new FileDelta(file1, file2, "Property added: {0}", k));
				continue;
			}
			Object v2 = prop2.get(k);
			if (v2 == null) {
				dest.accept(new FileDelta(file1, file2, "Property removed {0}", k));
				continue;
			}
			if (!Objects.equals(v1, v2)) {
				dest.accept(new FileDelta(file1, file2, "Property changed {0}", k));
			}
		}
	}

}
