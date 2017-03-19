package com.github.pms1.tppt.p2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.linesOf;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FormatTest {

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private <T> void run(String resource, AbstractRepositoryFactory<T> factory) throws Exception {

		Path path = Paths.get(getClass().getResource(resource).toURI());

		List<String> old = Assertions.linesOf(path.toFile());

		T repo;

		try (InputStream is = Files.newInputStream(path)) {
			repo = factory.read(is);
		}

		Path temp = folder.newFile().toPath();

		try (OutputStream os = Files.newOutputStream(temp)) {
			factory.write(repo, os);
		}

		boolean showDiff = false;
		if (showDiff) {
			int diff = 0;

			List<String> n = Assertions.linesOf(temp.toFile());
			for (int i = 0; i != old.size(); ++i) {
				String s1 = old.get(i);
				String s2 = n.get(i);
				if (s1.equals(s2)) {
					System.err.println("  " + s1);
					// diff = 0;
				} else {
					System.err.println("< " + s1);
					System.err.println("> " + s2);
					++diff;
				}

				if (diff == 500)
					break;
			}
		}

		assertThat(linesOf(temp.toFile())).isEqualTo(old);
	}

	@Test
	public void metadata() throws Exception {
		run("format/metadata-1.xml", plexusContainer.lookup(MetadataRepositoryFactory.class));
	}

	@Test
	public void artifact() throws Exception {
		run("format/artifact-1.xml", plexusContainer.lookup(ArtifactRepositoryFactory.class));
	}

	@Test
	@Ignore("original from http://download.eclipse.org/releases/neon/ is inconsistent, not comparable")
	public void compositeMetadata() throws Exception {
		run("format/composite-metadata-1.xml", plexusContainer.lookup(CompositeMetadataRepositoryFactory.class));
	}

	@Test
	@Ignore("original from http://download.eclipse.org/releases/neon/ is inconsistent, not comparable")
	public void compositeArtifact() throws Exception {
		run("format/composite-artifact-1.xml", plexusContainer.lookup(CompositeArtifactRepositoryFactory.class));
	}
}
