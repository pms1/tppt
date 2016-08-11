package com.github.pms1.tppt.p2;

import java.io.InputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArtifactRepositoryReaderSmokeTest {

	@Rule
	public TestResourceRule rule;

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	private final String artifactXml;

	@Parameters(name = "{0}")
	public static Object[] parameters() {
		return new Object[] { "artifactsNoArtifacts.xml", "artifactsOnlyRepositoryProperties.xml",
				"artifactsAttributeDescription.xml", "artifactsProcessing.xml", "artifactsProcessingData.xml",
				"artifactsOtherOrder.xml", "artifactsMissingArtifacts.xml" };
	}

	public ArtifactRepositoryReaderSmokeTest(String artifactXml) {
		this.artifactXml = artifactXml;
	}

	@Test
	public void read() throws Exception {
		ArtifactRepositoryFactory lookup = plexusContainer.lookup(ArtifactRepositoryFactory.class);
		try (InputStream in = getClass().getResourceAsStream(artifactXml)) {
			assert in != null;
			lookup.read(in);
		}
	}
}
