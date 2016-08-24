package com.github.pms1.tppt.p2;

import java.io.InputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MetadataRepositoryReaderSmokeTest {

	@Rule
	public TestResourceRule rule;

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	private final String contentXml;

	@Parameters(name = "{0}")
	public static Object[] parameters() {
		return new Object[] { "contentEmpty.xml", "contentOtherOrder.xml", "contentLicensesCopyright.xml",
				"contentReferences.xml", "contentLicenseNoAttributes.xml", "contentPatch.xml",
				"contentHostRequirementsGenerationMatch.xml", "contentNoProperties.xml", "contentNoTouchpoint.xml",
				"contentMultiple.xml", "contentAttributeDescription.xml", "contentOrderReferenceNoUriUrl.xml",
				"contentNoProvides.xml", "contentPropertiesNoSize.xml", "contentMetaRequirements.xml",
				"contentUpdateMatch.xml", "contentUpdateDescription.xml", "contentInstructionImport.xml" };
	}

	public MetadataRepositoryReaderSmokeTest(String contentXml) {
		this.contentXml = contentXml;
	}

	@Test
	public void read() throws Exception {
		MetadataRepositoryFactory lookup = plexusContainer.lookup(MetadataRepositoryFactory.class);
		try (InputStream in = getClass().getResourceAsStream(contentXml)) {
			assert in != null;
			lookup.read(in);
		}
	}
}
