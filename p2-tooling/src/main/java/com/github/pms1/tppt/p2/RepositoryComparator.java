package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

@Component(role = RepositoryComparator.class)
public class RepositoryComparator {
	@Requirement
	Logger logger;

	@Requirement
	ArtifactRepositoryFactory factory;

	@Requirement
	Map<String, FileComparator> comparators;

	public boolean run(Path pr1, Path pr2) throws IOException {
		ArtifactRepository r1 = factory.read(pr1);
		ArtifactRepository r2 = factory.read(pr2);

		Map<String, Artifact> m1 = new HashMap<>();
		Map<String, Artifact> m2 = new HashMap<>();
		for (Artifact a : r1.getArtifacts().values()) {
			Artifact old = m1.put(a.getId().getId(), a);
			if (old != null)
				throw new Error();
		}
		for (Artifact a : r2.getArtifacts().values()) {
			Artifact old = m2.put(a.getId().getId(), a);
			if (old != null)
				throw new Error();
		}

		boolean equal = true;
		List<Delta> dest = new ArrayList<>();

		List<Change> changes = new LinkedList<>();

		for (Map.Entry<String, Artifact> e1 : m1.entrySet()) {
			Artifact a2 = m2.get(e1.getKey());
			if (a2 == null) {
				dest.add(new ArtifactRemovedDelta(FileId.newRoot(pr1), FileId.newRoot(pr2),
						"Artifact removed: '" + e1.getValue().getId() + "'", e1.getValue().getId()));
				continue;
			}

			Path p1 = Paths.get(r1.getArtifactUri(e1.getValue().getId()));
			Path p2 = Paths.get(r2.getArtifactUri(a2.getId()));

			String classifier1 = e1.getValue().getClassifier();
			String classifier2 = a2.getClassifier();
			if (!classifier1.equals(classifier2)) {
				dest.add(
						new ArtifactClassifierDelta(
								FileId.newRoot(pr1), FileId.newRoot(pr2), "Artifact '" + e1.getKey()
										+ "' classifier changed: '" + classifier1 + "' -> '" + classifier2 + "'",
								e1.getKey()));
				continue;
			}

			FileId file1 = FileId.newRoot(p1);
			FileId file2 = FileId.newRoot(p2);

			FileComparator comparator = null;
			switch (classifier1) {
			case "osgi.bundle":
				comparator = comparators.get(BundleComparator.HINT);
				changes.add(new BundleVersionChange(e1.getKey(), file1, e1.getValue().getId().getVersion(), file2,
						a2.getId().getVersion()));
				break;
			case "org.eclipse.update.feature":
				comparator = comparators.get(FeatureComparator.HINT);
				changes.add(new FeatureVersionChange(e1.getKey(), file1, e1.getValue().getId().getVersion(), file2,
						a2.getId().getVersion()));
				break;
			case "binary":
				comparator = comparators.get(BinaryComparator.HINT);
				break;
			default:
				throw new Error("Unhandled artifact classifier " + classifier1);
			}

			comparator.compare(file1, p1, file2, p2, dest::add);
		}

		for (Map.Entry<String, Artifact> e2 : m2.entrySet()) {
			Artifact a1 = m1.get(e2.getKey());
			if (a1 == null) {
				dest.add(new ArtifactAddedDelta(FileId.newRoot(pr1), FileId.newRoot(pr2),
						"Artifact added: '" + e2.getValue().getId() + "'", e2.getValue().getId()));
			}
		}

		if (equal) {
			for (Delta d : dest) {

				boolean ok = false;
				for (Change c : changes) {
					if (c.accept(d)) {
						ok = true;
						break;
					}
				}
				if (!ok) {
					logger.info("Incompatible change: " + d);
				}
			}
		}

		return equal;
	}

	static abstract class Change {
		abstract boolean accept(Delta delta);
	}

	static class FeatureVersionChange extends Change {
		private final String featureId;
		private final FileId file1;
		private final FileId file2;
		private final Version v1;
		private final Version v2;

		public FeatureVersionChange(String featureId, FileId file1, Version v1, FileId file2, Version v2) {
			Preconditions.checkNotNull(featureId);
			Preconditions.checkArgument(!featureId.isEmpty());
			this.featureId = featureId;
			Preconditions.checkNotNull(file1);
			this.file1 = file1;
			Preconditions.checkNotNull(v1);
			this.v1 = v1;
			Preconditions.checkNotNull(file2);
			this.file2 = file2;
			Preconditions.checkNotNull(v2);
			this.v2 = v2;
		}

		@Override
		boolean accept(Delta delta) {
			if (delta instanceof FeatureVersionDelta) {
				FeatureVersionDelta d = (FeatureVersionDelta) delta;
				if (!d.getBaselineFile().getParent().equals(file1))
					return false;
				if (!d.getCurrentFile().getParent().equals(file2))
					return false;
				if (!Objects.equals(v1, d.getBaselineVersion()))
					return false;
				if (!Objects.equals(v2, d.getCurrentVersion()))
					return false;
				return true;
			} else {
				return false;
			}
		}

	}

	static class BundleVersionChange extends Change {
		private final String bundleId;
		private final FileId file1;
		private final FileId file2;
		private final Version v1;
		private final Version v2;

		public BundleVersionChange(String bundleId, FileId file1, Version v1, FileId file2, Version v2) {
			Preconditions.checkNotNull(bundleId);
			Preconditions.checkArgument(!bundleId.isEmpty());
			this.bundleId = bundleId;
			Preconditions.checkNotNull(file1);
			this.file1 = file1;
			Preconditions.checkNotNull(v1);
			this.v1 = v1;
			Preconditions.checkNotNull(file2);
			this.file2 = file2;
			Preconditions.checkNotNull(v2);
			this.v2 = v2;
		}

		@Override
		boolean accept(Delta delta) {
			if (delta instanceof ManifestVersionDelta) {
				ManifestVersionDelta d = (ManifestVersionDelta) delta;
				if (!d.getBaselineFile().getParent().equals(file1))
					return false;
				if (!d.getCurrentFile().getParent().equals(file2))
					return false;
				if (!Objects.equals(v1, d.getBaselineVersion()))
					return false;
				if (!Objects.equals(v2, d.getCurrentVersion()))
					return false;
				return true;
			} else if (delta instanceof ManifestEclipseSourceBundleVersionDelta) {
				ManifestEclipseSourceBundleVersionDelta d = (ManifestEclipseSourceBundleVersionDelta) delta;
				if (!bundleId.equals(d.getBundleId()))
					return false;
				if (!Objects.equals(v1, d.getBaselineVersion()))
					return false;
				if (!Objects.equals(v2, d.getCurrentVersion()))
					return false;
				return true;
			} else if (delta instanceof ManifestHeaderDelta) {
				ManifestHeaderDelta d = (ManifestHeaderDelta) delta;
				if (!d.getBaselineFile().getParent().equals(file1))
					return false;
				if (!d.getCurrentFile().getParent().equals(file2))
					return false;
				if (!d.getKey().equals("Bnd-LastModified"))
					return false;

				return true;
			} else if (delta instanceof FeaturePluginVersionDelta) {
				FeaturePluginVersionDelta d = (FeaturePluginVersionDelta) delta;
				if (!d.getPluginId().equals(bundleId))
					return false;
				if (!Objects.equals(v1, d.getBaselineVersion()))
					return false;
				if (!Objects.equals(v2, d.getCurrentVersion()))
					return false;
				return true;
			} else {
				return false;
			}
		}

	}
}
