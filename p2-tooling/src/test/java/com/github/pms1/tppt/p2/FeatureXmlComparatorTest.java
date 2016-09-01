package com.github.pms1.tppt.p2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.Version;

public class FeatureXmlComparatorTest {
	@Rule
	public TestResourceRule testResources = new TestResourceRule();

	@Rule
	public PlexusContainerRule plexusContainer = new PlexusContainerRule();

	@Test
	public void x3() throws Exception {
		Path p1 = testResources.getResource("feature1.xml");
		Path p2 = testResources.getResource("feature2.xml");

		FileComparator lookup = plexusContainer.lookup(FileComparator.class, "feature.xml");

		List<FileDelta> deltas = new ArrayList<>();
		lookup.compare(FileId.newRoot(p1), p1, FileId.newRoot(p2), p2, deltas::add);

		Assertions.assertThat(deltas).containsExactlyInAnyOrder( //
				new FeaturePluginVersionDelta(FileId.newRoot(p1), FileId.newRoot(p2),
						"net.sf.jopt-simple.jopt-simple.source", Version.parseVersion("5.0.1"),
						Version.parseVersion("5.0.2")),
				new FeaturePluginVersionDelta(FileId.newRoot(p1), FileId.newRoot(p2), "net.sf.jopt-simple.jopt-simple",
						Version.parseVersion("5.0.1"), Version.parseVersion("5.0.2")));

	}

	@Test
	public void x4() throws Exception {
		Path p1 = testResources.getResource("feature1.xml");
		Path p2 = testResources.getResource("feature3.xml");

		FileComparator lookup = plexusContainer.lookup(FileComparator.class, "feature.xml");
		List<FileDelta> deltas = new ArrayList<>();
		lookup.compare(FileId.newRoot(p1), p1, FileId.newRoot(p2), p2, deltas::add);
		Assertions.assertThat(deltas).containsExactlyInAnyOrder(
				new FileDelta(FileId.newRoot(p1), FileId.newRoot(p2), "Plugin {0} attribute {1} changed {2} -> {3}",
						"unpack", true, false),
				new FileDelta(FileId.newRoot(p1), FileId.newRoot(p2), "Plugin {0} attribute {1} changed {2} -> {3}",
						"unpack", true, false),
				new FeaturePluginVersionDelta(FileId.newRoot(p1), FileId.newRoot(p2),
						"net.sf.jopt-simple.jopt-simple.source", Version.parseVersion("5.0.1"),
						Version.parseVersion("5.0.2")),
				new FeaturePluginVersionDelta(FileId.newRoot(p1), FileId.newRoot(p2), "net.sf.jopt-simple.jopt-simple",
						Version.parseVersion("5.0.1"), Version.parseVersion("5.0.2")));
	}
}
