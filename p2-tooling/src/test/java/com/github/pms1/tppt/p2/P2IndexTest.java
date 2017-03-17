package com.github.pms1.tppt.p2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.github.pms1.tppt.p2.P2RepositoryFactory.P2Index;

public class P2IndexTest {

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	private Map<String, DataCompression> compressions;
	private DataCompression xml;
	private DataCompression jar;

	@Before
	public void init() throws Exception {
		compressions = plexusContainer.lookupMap(DataCompression.class);
		xml = compressions.get("xml");
		jar = compressions.get("jar");
	}

	@Test
	public void test1() throws Exception {
		Path path = Paths.get(getClass().getResource("p2-1").toURI());

		P2Index index = new P2RepositoryFactory.P2Index(path);

		List<DataCompression> list = index.getPossibleCompressions(P2RepositoryFactory.P2Kind.artifact, compressions);

		assertThat(list).containsExactly(jar, xml);

		// FIXME: if .xz is there, put a different entry into the p2.index and
		// check that a/m are handled correctly
		list = index.getPossibleCompressions(P2RepositoryFactory.P2Kind.metadata, compressions);

		assertThat(list).containsExactly(jar, xml);
	}
}
