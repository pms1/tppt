package com.github.pms1.tppt.p2;

import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class P2RepositoryFactoryTestXXX {

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void create() throws Exception {
		P2RepositoryFactory factory = plexusContainer.lookup(P2RepositoryFactory.class);

		factory.loadAny(Paths.get("c:/temp/agg2"));
	}
}
