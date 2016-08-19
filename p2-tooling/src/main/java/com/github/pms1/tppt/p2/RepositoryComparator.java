package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.osgi.framework.Version;
import org.slf4j.Logger;

import com.github.pms1.ocomp.DecomposedObject;
import com.github.pms1.ocomp.Decomposer;
import com.github.pms1.ocomp.DecomposerMatchers;
import com.github.pms1.ocomp.ObjectComparator;
import com.github.pms1.ocomp.ObjectComparator.ChangeType;
import com.github.pms1.ocomp.ObjectComparator.DecomposerFactory;
import com.github.pms1.ocomp.ObjectComparator.DeltaCreator;
import com.github.pms1.ocomp.ObjectComparator.OPath;
import com.github.pms1.ocomp.ObjectComparator.OPath2;
import com.github.pms1.ocomp.ObjectComparatorBuilder;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataArtifact;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Property;
import com.github.pms1.tppt.p2.jaxb.metadata.Provided;
import com.github.pms1.tppt.p2.jaxb.metadata.Required;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;

@Component(role = RepositoryComparator.class)
public class RepositoryComparator {
	@Requirement
	Logger logger;

	@Requirement
	ArtifactRepositoryFactory artifactRepositoryFactory;

	@Requirement
	MetadataRepositoryFactory metadataRepositoryFactory;

	@Requirement
	Map<String, FileComparator> comparators;

	static private final TypeToken<?> listRequired = new TypeToken<List<Required>>() {

	};

	static private final TypeToken<?> listProvided = new TypeToken<List<Provided>>() {

	};

	static DecomposerFactory xxx = new DecomposerFactory() {

		@Override
		public <T> Decomposer<T> generate(Type t) {
			if (!(t instanceof ParameterizedType))
				return null;

			ParameterizedType pt = (ParameterizedType) t;
			if (!List.class.isAssignableFrom((Class<?>) pt.getRawType()))
				return null;
			if (pt.getActualTypeArguments().length != 1)
				throw new IllegalStateException();
			Type et = pt.getActualTypeArguments()[0];

			return o -> {
				DecomposedObject r = new DecomposedObject();

				OPath path = OPath.index("*");
				for (Object r1 : (List) o) {
					r.put(null, et, r1);
				}

				return r;
			};
		}

	};

	static class UnitDelta extends Delta {
		final Unit left;
		final Unit right;
		final OPath2 path;

		UnitDelta(Unit left, Unit right, OPath2 path) {
			this.left = left;
			this.right = right;
			this.path = path;
		}

		@Override
		public String toString() {
			return "UnitDelta(" + render(left) + "," + render(right) + "," + path + ")";
		}
	}

	static class ProvidedAdded extends Delta {
		final Unit left;
		final Unit right;
		final Provided provided;

		ProvidedAdded(Unit left, Unit right, Provided provided) {
			Preconditions.checkNotNull(left);
			this.left = left;
			Preconditions.checkNotNull(right);
			this.right = right;
			Preconditions.checkNotNull(provided);
			this.provided = provided;
		}

		@Override
		public String toString() {
			return "ProvidedAdded(" + render(left) + "," + render(right) + "," + render(provided) + ")";
		}
	}

	static class RequiredAdded extends Delta {
		final Unit left;
		final Unit right;
		final Required required;

		RequiredAdded(Unit left, Unit right, Required required) {
			Preconditions.checkNotNull(left);
			this.left = left;
			Preconditions.checkNotNull(right);
			this.right = right;
			Preconditions.checkNotNull(required);
			this.required = required;
		}

		@Override
		public String toString() {
			return "RequiredAdded(" + render(left) + "," + render(right) + "," + render(required) + ")";
		}
	}

	static class ProvidedRemoved extends Delta {
		final Unit left;
		final Unit right;
		final Provided provided;

		ProvidedRemoved(Unit left, Unit right, Provided provided) {
			Preconditions.checkNotNull(left);
			this.left = left;
			Preconditions.checkNotNull(right);
			this.right = right;
			Preconditions.checkNotNull(provided);
			this.provided = provided;
		}

		@Override
		public String toString() {
			return "ProvidedRemoved(" + render(left) + "," + render(right) + "," + render(provided) + ")";
		}
	}

	static class RequiredRemoved extends Delta {
		final Unit left;
		final Unit right;
		final Required required;

		RequiredRemoved(Unit left, Unit right, Required required) {
			Preconditions.checkNotNull(left);
			this.left = left;
			Preconditions.checkNotNull(right);
			this.right = right;
			Preconditions.checkNotNull(required);
			this.required = required;
		}

		@Override
		public String toString() {
			return "RequiredRemoved(" + render(left) + "," + render(right) + "," + render(required) + ")";
		}
	}

	ObjectComparator<Delta> oc = ObjectComparatorBuilder.newBuilder()
			.addDecomposer(DecomposerMatchers.isAssignable(listRequired), xxx)
			.addDecomposer(DecomposerMatchers.isAssignable(listProvided), xxx)
			.addDecomposer("//units/unit", new Decomposer<List<Unit>>() {

				DecomposedObject dc1(List<Unit> units, Function<Unit, String> f) {
					DecomposedObject r = new DecomposedObject();

					for (Unit u : units) {
						if (!r.put(OPath.index(f.apply(u)), u))
							return null;
					}

					return r;
				}

				@Override
				public DecomposedObject decompose(List<Unit> o) {
					DecomposedObject r;

					r = dc1(o, p -> p.getId());
					if (r != null)
						return r;

					r = dc1(o, p -> p.getId() + "/" + p.getVersion());
					if (r != null)
						return r;

					throw new Error();
				}

			})
			// ObjectComparator.<Unit>listToMapDecomposer(p -> p.getId() + "/" +
			// p.getVersion()))
			.addDecomposer("//properties/property", ObjectComparator.<Property>listToMapDecomposer(p -> p.getName()))
			.setDeltaCreator(new DeltaCreator<Delta>() {

				@Override
				public Delta changed(OPath2 p, ChangeType change, Object m1, Object m2) {
					System.err.println("X " + p.getPath() + " " + change + " " + m1 + " " + m2);
					if (p.size() > 3) {
						OPath2 unitPath = p.subPath(0, 3);
						if (unitPath.getPath().equals("//units/unit")) {

							OPath2 rel = p.subPath(4);
							if (rel == null) {
								return new MetadataDelta(p, change);
							} else {
								Unit uleft = (Unit) p.subPath(3, 4).getLeft();
								Unit uright = (Unit) p.subPath(3, 4).getRight();

								switch (rel.getPath()) {
								case "/provides/provided":
									switch (change) {
									case ADDED:
										return new ProvidedAdded(uleft, uright, (Provided) m2);
									case REMOVED:
										return new ProvidedRemoved(uleft, uright, (Provided) m1);
									default:
										throw new Error();
									}
								case "/requires/required":
									switch (change) {
									case ADDED:
										return new RequiredAdded(uleft, uright, (Required) m2);
									case REMOVED:
										return new RequiredRemoved(uleft, uright, (Required) m1);
									default:
										throw new Error();
									}
								}
								for (int i = 0; i != p.size(); ++i) {
									OPath2 p1 = p.subPath(i, i + 1);
									System.err.println("P1 " + p1 + " " + p1.getLeft() + " " + p1.getRight());
								}
								for (int i = 0; i != rel.size(); ++i) {
									OPath2 p1 = rel.subPath(i, i + 1);
									System.err.println("R " + p1 + " " + p1.getLeft() + " " + p1.getRight());
								}

								switch (change) {
								case CHANGED:
									return new UnitDelta((Unit) p.subPath(3, 4).getLeft(),
											(Unit) p.subPath(3, 4).getRight(), rel);
								default:
									throw new Error();
								}

							}
						}
					}
					return new MetadataDelta(p, change);

				}

			}).build();

	static class MetadataDelta extends Delta {
		private final OPath2 path;
		private final ChangeType change;

		public MetadataDelta(OPath2 p, ChangeType change) {
			this.path = p;
			this.change = change;
		}

	}

	public boolean run(Path pr1, Path pr2) throws IOException {
		MetadataRepository md1 = metadataRepositoryFactory.readRepository(pr1);
		MetadataRepository md2 = metadataRepositoryFactory.readRepository(pr2);

		ArtifactRepository r1 = artifactRepositoryFactory.read(pr1);
		ArtifactRepository r2 = artifactRepositoryFactory.read(pr2);

		List<Delta> dest = new ArrayList<>();

		List<Change> changes = new LinkedList<>();

		Pattern p = Pattern.compile("//units/unit\\[(?<unit>[^\\]]+)\\](?<detail>.*)");

		dest.addAll(oc.compare(md1, md2));

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

		boolean equal = true;
		for (Delta d : dest) {

			boolean ok = false;
			for (Change c : changes) {
				if (c.accept(d)) {
					ok = true;
					break;
				}
			}
			if (!ok) {
				System.err.println("Incompatible change: " + d);
				logger.info("Incompatible change: " + d);
				equal = false;
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
			} else if (delta instanceof ProvidedAdded) {
				ProvidedAdded d = (ProvidedAdded) delta;

				// check unit is our right feature
				if (hasOnlyArtifact(d.right, "org.eclipse.update.feature", featureId, v2)) {
					if (isEqual(d.provided, "org.eclipse.equinox.p2.iu", featureId + ".feature.jar", v2))
						return true;
					if (isEqual(d.provided, "org.eclipse.update.feature", featureId, v2))
						return true;
				}

				if (is(d.right, featureId + ".feature.group", v2)) {
					if (isEqual(d.provided, "org.eclipse.equinox.p2.iu", featureId + ".feature.group", v2))
						return true;
				}

				return false;
			} else if (delta instanceof RequiredAdded) {
				RequiredAdded d = (RequiredAdded) delta;
				return false;
			} else if (delta instanceof ProvidedRemoved) {
				ProvidedRemoved d = (ProvidedRemoved) delta;

				// check unit is our left feature
				if (hasOnlyArtifact(d.left, "org.eclipse.update.feature", featureId, v1)) {
					if (isEqual(d.provided, "org.eclipse.equinox.p2.iu", featureId + ".feature.jar", v1))
						return true;
					if (isEqual(d.provided, "org.eclipse.update.feature", featureId, v1))
						return true;
				}

				if (is(d.left, featureId + ".feature.group", v1)) {
					if (isEqual(d.provided, "org.eclipse.equinox.p2.iu", featureId + ".feature.group", v1))
						return true;
				}

				return false;
			} else {
				return false;
			}
		}
	}

	static boolean is(Unit u, String id, Version version) {
		return u.getId().equals(id) && u.getVersion().equals(version);
	}

	static boolean isEqual(Provided p, String namespace, String name, Version version) {
		return p.getNamespace().equals(namespace) && p.getName().equals(name) && p.getVersion().equals(version);
	}

	static boolean hasOnlyArtifact(Unit u, String classifier, String id, Version version) {
		return u.getArtifacts() != null && u.getArtifacts().getArtifact().size() == 1
				&& u.getArtifacts().getArtifact().stream().allMatch(p -> p.getClassifier().equals(classifier)
						&& p.getId().equals(id) && p.getVersion().equals(version));
	}

	static Version featureVersion = VersionParser.valueOf("1.0.0");

	static boolean isFeature(Unit u) {
		return u.getProvides().getProvided().stream()
				.anyMatch(p -> p.getNamespace().equals("org.eclipse.equinox.p2.eclipse.type") //
						&& p.getName().equals("feature") //
						&& p.getVersion().equals(featureVersion));

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
			} else if (delta instanceof ProvidedAdded) {
				ProvidedAdded d = (ProvidedAdded) delta;
				return false;
			} else if (delta instanceof UnitDelta) {
				UnitDelta d = (UnitDelta) delta;
				// System.err.println("XX " + render(d.left) + " " +
				// render(d.right) + " -> " + d.path + " -> "
				// + render(d.path.getLeft()));
				return false;
			} else {
				return false;
			}
		}

	}

	static String render(Object o) {
		if (o instanceof MetadataArtifact) {
			MetadataArtifact p = (MetadataArtifact) o;
			return "MetadataArtifact(" + p.getClassifier() + "," + p.getId() + "," + p.getVersion() + ")";

		}
		if (o instanceof List) {
			StringBuilder b = new StringBuilder();
			b.append("[");
			List<Object> l = (List<Object>) (List<?>) o;
			return "[" + l.stream().map(RepositoryComparator::render).collect(Collectors.joining(", ")) + "]";
		}
		if (o instanceof Provided) {
			Provided p = (Provided) o;
			return "Provided(" + p.getNamespace() + "," + p.getName() + "," + p.getVersion() + ")";
		}

		if (o instanceof Required) {
			Required p = (Required) o;
			return "Required(" + p.getNamespace() + "," + p.getName() + "," + p.getRange() + "," + p.getFilter() + ","
					+ p.getMatch() + "," + p.getMatchParameters() + "," + p.getMax() + "," + p.getMin() + ")";
		}

		if (o instanceof Unit) {
			Unit p = (Unit) o;
			return "Unit(" + p.getId() + "," + p.getVersion() + ")";
		}

		return String.valueOf(o);
	}
}
