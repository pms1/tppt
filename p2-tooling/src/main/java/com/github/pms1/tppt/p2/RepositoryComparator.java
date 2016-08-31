package com.github.pms1.tppt.p2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.slf4j.Logger;

import com.github.pms1.ldap.SearchFilter;
import com.github.pms1.ldap.SearchFilterPrinter;
import com.github.pms1.ocomp.ComparatorMatchers;
import com.github.pms1.ocomp.DecomposedObject;
import com.github.pms1.ocomp.Decomposer;
import com.github.pms1.ocomp.DecomposerMatchers;
import com.github.pms1.ocomp.OPathMatcher;
import com.github.pms1.ocomp.ObjectComparator;
import com.github.pms1.ocomp.ObjectComparator.ChangeType;
import com.github.pms1.ocomp.ObjectComparator.DecomposerFactory;
import com.github.pms1.ocomp.ObjectComparator.DeltaCreator;
import com.github.pms1.ocomp.ObjectComparator.OPath;
import com.github.pms1.ocomp.ObjectComparator.OPath2;
import com.github.pms1.ocomp.ObjectComparatorBuilder;
import com.github.pms1.ocomp.ObjectDelta;
import com.github.pms1.tppt.p2.jaxb.metadata.Instruction;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataArtifact;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Property;
import com.github.pms1.tppt.p2.jaxb.metadata.Provided;
import com.github.pms1.tppt.p2.jaxb.metadata.Required;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
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

	static DecomposerFactory listToBag = new DecomposerFactory() {

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

				for (Object r1 : (List) o) {
					r.put(null, et, r1);
				}

				return r;
			};
		}

	};

	static abstract class AbstractUnitDelta extends FileDelta {
		protected final Unit left;
		protected final Unit right;

		AbstractUnitDelta(FileId id1, Unit left, FileId id2, Unit right) {
			super(id1, id2, "FIXME");
			Preconditions.checkNotNull(left);
			this.left = left;
			Preconditions.checkNotNull(right);
			this.right = right;
		}
	}

	static class UnitDelta extends AbstractUnitDelta {
		final OPath2 path;

		UnitDelta(FileId id1, Unit left, FileId id2, Unit right, OPath2 path) {
			super(id1, left, id2, right);
			this.path = path;
		}

		@Override
		public String toString() {
			return "UnitDelta(" + render(left) + "," + render(right) + "," + path.getPath() + "," + path.getLeft()
					+ " -> " + path.getRight() + ")";
		}
	}

	static class ProvidedAdded extends AbstractUnitDelta {
		final Provided provided;

		ProvidedAdded(FileId id1, Unit left, FileId id2, Unit right, Provided provided) {
			super(id1, left, id2, right);
			Preconditions.checkNotNull(provided);
			this.provided = provided;
		}

		@Override
		public String toString() {
			return "ProvidedAdded(" + render(left) + "," + render(right) + "," + render(provided) + ")";
		}
	}

	static class RequiredAdded extends AbstractUnitDelta {
		final Required required;

		RequiredAdded(FileId id1, Unit left, FileId id2, Unit right, Required required) {
			super(id1, left, id2, right);
			Preconditions.checkNotNull(required);
			this.required = required;
		}

		@Override
		public String toString() {
			return "RequiredAdded(" + render(left) + "," + render(right) + "," + render(required) + ")";
		}
	}

	static class ProvidedRemoved extends AbstractUnitDelta {
		final Provided provided;

		ProvidedRemoved(FileId id1, Unit left, FileId id2, Unit right, Provided provided) {
			super(id1, left, id2, right);
			Preconditions.checkNotNull(provided);
			this.provided = provided;
		}

		@Override
		public String toString() {
			return "ProvidedRemoved(" + render(left) + "," + render(right) + "," + render(provided) + ")";
		}
	}

	static class RequiredRemoved extends AbstractUnitDelta {
		final Required required;

		RequiredRemoved(FileId id1, Unit left, FileId id2, Unit right, Required required) {
			super(id1, left, id2, right);

			Preconditions.checkNotNull(required);
			this.required = required;
		}

		@Override
		public String toString() {
			return "RequiredRemoved(" + render(left) + "," + render(right) + "," + render(required) + ")";
		}
	}

	static final SearchFilterPrinter printer = new SearchFilterPrinter();

	static ObjectComparatorBuilder<ObjectDelta> oc1 = ObjectComparatorBuilder.newBuilder()
			.addComparator(ComparatorMatchers.isAssignable(TypeToken.of(SearchFilter.class)), (a, b) -> {
				return printer.print((SearchFilter) a).equals(printer.print((SearchFilter) b));
			}).addDecomposer(DecomposerMatchers.isAssignable(listRequired), listToBag)
			.addDecomposer(DecomposerMatchers.isAssignable(listProvided), listToBag)
			.addDecomposer("//units/unit[*]/touchpointData/instructions[*]/instruction[manifest]/value",
					new Decomposer<String>() {

						@Override
						public DecomposedObject decompose(String value) {

							try {
								Map<String, String> ml = ManifestElement.parseBundleManifest(
										new ByteArrayInputStream(value.trim().getBytes(StandardCharsets.UTF_8)), null);

								DecomposedObject decomposedObject = new DecomposedObject();
								for (Map.Entry<String, String> e : ml.entrySet()) {
									decomposedObject.put(OPath.index(e.getKey()), e.getValue());
								}
								return decomposedObject;
							} catch (IOException | BundleException e) {
								throw new RuntimeException(e);
							}
						}

					})
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
			.addDecomposer("//units/unit[*]/touchpointData/instructions[*]/instruction",
					ObjectComparator.<Instruction>listToMapDecomposer(p -> p.getKey()))
			.addDecomposer("//units/unit[*]/artifacts/artifact",
					ObjectComparator.<MetadataArtifact>listToMapDecomposer(p -> p.getId() + "/" + p.getClassifier()))
			.addDecomposer("//properties/property", ObjectComparator.<Property>listToMapDecomposer(p -> p.getName()));

	static class MetadataDelta extends FileDelta {
		private final OPath2 path;
		private final ChangeType change;

		public MetadataDelta(FileId id1, FileId id2, OPath2 p, ChangeType change) {
			super(id1, id2, "FIXME");
			this.path = p;
			this.change = change;
		}

		@Override
		public String toString() {
			return "MetadataDelta(" + path.getPath() + "," + change + "," + render(path.getLeft()) + " -> "
					+ render(path.getRight()) + ")";
		}
	}

	private void compare(FileId root1, Set<DataCompression> s1, FileId root2, Set<DataCompression> s2, String prefix,
			Consumer<FileDelta> dest) {
		for (DataCompression s : Sets.union(s1, s2)) {
			boolean has1 = s1.contains(s);
			boolean has2 = s2.contains(s);

			if (has1 && !has2) {
				dest.accept(new FileDelta(root1, root2, "File removed: '" + prefix + "." + s.getFileSuffix() + "'"));
			} else if (!has1 && has2) {
				dest.accept(new FileDelta(root1, root2, "File added: '" + prefix + "." + s.getFileSuffix() + "'"));
			}
		}
	}

	public boolean run(P2Repository pr1, P2Repository pr2) throws IOException {
		List<FileDelta> dest = new ArrayList<>();

		FileId root1 = FileId.newRoot(pr1.getPath());
		FileId root2 = FileId.newRoot(pr2.getPath());

		compare(root1, pr1.getArtifactDataCompressions(), root2, pr2.getArtifactDataCompressions(),
				P2RepositoryFactory.ARTIFACT_PREFIX, dest::add);
		compare(root1, pr1.getMetadataDataCompressions(), root2, pr2.getMetadataDataCompressions(),
				P2RepositoryFactory.METADATA_PREFIX, dest::add);

		MetadataRepositoryFacade mdf1 = pr1.getMetadataRepositoryFacade();
		FileId mdf1id = FileId.newRoot(mdf1.getPath());
		MetadataRepositoryFacade mdf2 = pr2.getMetadataRepositoryFacade();
		FileId mdf2id = FileId.newRoot(mdf2.getPath());

		MetadataRepository md1 = mdf1.getMetadata();
		MetadataRepository md2 = mdf2.getMetadata();

		ArtifactRepositoryFacade r1 = pr1.getArtifactRepositoryFacade();
		FileId r1id = FileId.newRoot(r1.getPath());
		ArtifactRepositoryFacade r2 = pr2.getArtifactRepositoryFacade();
		FileId r2id = FileId.newRoot(r2.getPath());

		List<Change> changes = new LinkedList<>();

		ObjectComparator<FileDelta> oc = oc1.setDeltaCreator(new DeltaCreator<FileDelta>() {

			@Override
			public FileDelta changed(OPath2 p, ChangeType change, Object m1, Object m2) {
				if (p.size() > 3) {
					OPath2 unitPath = p.subPath(0, 3);
					if (unitPath.getPath().equals("//units/unit")) {

						OPath2 rel = p.subPath(4);
						if (rel == null) {
							return new MetadataDelta(mdf1id, mdf2id, p, change);
						} else {
							Unit uleft = (Unit) p.subPath(3, 4).getLeft();
							Unit uright = (Unit) p.subPath(3, 4).getRight();

							switch (rel.getPath()) {
							case "/provides/provided":
								switch (change) {
								case ADDED:
									return new ProvidedAdded(mdf1id, uleft, mdf2id, uright, (Provided) m2);
								case REMOVED:
									return new ProvidedRemoved(mdf1id, uleft, mdf2id, uright, (Provided) m1);
								default:
									throw new Error();
								}
							case "/requires/required":
								switch (change) {
								case ADDED:
									return new RequiredAdded(mdf1id, uleft, mdf2id, uright, (Required) m2);
								case REMOVED:
									return new RequiredRemoved(mdf1id, uleft, mdf2id, uright, (Required) m1);
								default:
									throw new Error();
								}
							}

							switch (change) {
							case CHANGED:
								return new UnitDelta(mdf1id, (Unit) p.subPath(3, 4).getLeft(), mdf2id,
										(Unit) p.subPath(3, 4).getRight(), rel);
							default:
								throw new Error();
							}

						}
					}
				}

				// FIXME: here?
				switch (p.getPath()) {
				case "//properties/property[p2.timestamp]/value":
					return null;
				}

				return new MetadataDelta(mdf1id, mdf2id, p, change);
			}

		}).build();

		dest.addAll(oc.compare(md1, md2));

		Map<String, ArtifactFacade> m1 = new HashMap<>();
		Map<String, ArtifactFacade> m2 = new HashMap<>();
		for (ArtifactFacade a : r1.getArtifacts().values()) {
			ArtifactFacade old = m1.put(a.getId().getId(), a);
			if (old != null)
				throw new Error();
		}
		for (ArtifactFacade a : r2.getArtifacts().values()) {
			ArtifactFacade old = m2.put(a.getId().getId(), a);
			if (old != null)
				throw new Error();
		}

		for (Map.Entry<String, ArtifactFacade> e1 : m1.entrySet()) {
			ArtifactFacade a2 = m2.get(e1.getKey());
			if (a2 == null) {
				dest.add(new ArtifactRemovedDelta(r1id, r2id, "Artifact removed: " + render(e1.getValue()),
						e1.getValue().getId()));
				continue;
			}

			Path p1 = r1.getArtifactUri(e1.getValue().getId());
			Path p2 = r2.getArtifactUri(a2.getId());

			String classifier1 = e1.getValue().getClassifier();
			String classifier2 = a2.getClassifier();
			if (!classifier1.equals(classifier2)) {
				dest.add(new ArtifactClassifierDelta(r1id, r2id,
						"Artifact classifier changed: '" + render(e1.getValue()) + " -> " + render(a2), e1.getKey()));
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

		for (Map.Entry<String, ArtifactFacade> e2 : m2.entrySet()) {
			ArtifactFacade a1 = m1.get(e2.getKey());
			if (a1 == null) {
				dest.add(new ArtifactAddedDelta(r1id, r2id, "Artifact added: " + render(e2.getValue()),
						e2.getValue().getId()));
			}
		}

		List<String> incompatibleChanges = new ArrayList<>();

		for (Delta d : dest) {
			if (!changes.stream().anyMatch(c -> c.accept(d)))
				incompatibleChanges.add("Incompatible change: " + d);
		}

		for (Change c : changes)
			c.check(incompatibleChanges::add);

		incompatibleChanges.forEach(p -> logger.info("Incompatible change: " + p));

		return incompatibleChanges.isEmpty();
	}

	static abstract class Change {
		abstract boolean accept(Delta delta);

		abstract void check(Consumer<String> change);
	}

	private static final Predicate<SearchFilter> featureFilter = p -> p != null
			&& printer.print(p).equals("(org.eclipse.update.install.features=true)");

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
			} else if (delta instanceof RequiredAdded) {
				RequiredAdded d = (RequiredAdded) delta;

				if (isFeatureGroup(d)) {
					// if (is(d.right, featureId + ".feature.group", v2)) {
					if (new RequiredMatcher() //
							.withNamespace(p -> p.equals("org.eclipse.equinox.p2.iu"))
							.withName(p -> p.equals(featureId + ".feature.jar")) //
							.withRange(p -> Objects.equals(p,
									new VersionRange(VersionRange.LEFT_CLOSED, v2, v2, VersionRange.RIGHT_CLOSED))) //
							.withFilter(featureFilter) //
							.test(d.required))
						return true;
				}

				return false;
			} else if (delta instanceof RequiredRemoved) {
				RequiredRemoved d = (RequiredRemoved) delta;

				if (isFeatureGroup(d)) {
					// if (is(d.left, featureId + ".feature.group", v1)) {
					if (new RequiredMatcher() //
							.withNamespace(p -> p.equals("org.eclipse.equinox.p2.iu"))
							.withName(p -> p.equals(featureId + ".feature.jar")) //
							.withRange(p -> Objects.equals(p,
									new VersionRange(VersionRange.LEFT_CLOSED, v1, v1, VersionRange.RIGHT_CLOSED))) //
							.withFilter(featureFilter) //
							.test(d.required))
						return true;
				}

				return false;
			} else if (delta instanceof UnitDelta) {
				UnitDelta d = (UnitDelta) delta;

				if (isFeatureGroup(d)) {

					switch (d.path.getPath()) {
					case "/update/range":
						return d.path.getLeft()
								.equals(new VersionRange(VersionRange.LEFT_CLOSED, Version.emptyVersion, v1,
										VersionRange.RIGHT_OPEN))
								&& d.path.getRight().equals(new VersionRange(VersionRange.LEFT_CLOSED,
										Version.emptyVersion, v2, VersionRange.RIGHT_OPEN));
					case "/version":
						return d.path.getLeft().equals(v1) && d.path.getRight().equals(v2);
					default:
						return false;
					}
				} else if (isFeatureJar(d)) {
					if (d.path.getPath().equals("/version"))
						return d.path.getLeft().equals(v1) && d.path.getRight().equals(v2);
					else if (unitArtifactVersionMatcher.matches(d.path)) {
						MetadataArtifact l = (MetadataArtifact) d.path.getParent().getLeft();
						MetadataArtifact r = (MetadataArtifact) d.path.getParent().getRight();

						if (!l.getClassifier().equals("org.eclipse.update.feature"))
							return false;
						if (!l.getId().equals(featureId))
							return false;
						if (!l.getVersion().equals(v1))
							return false;
						if (!r.getClassifier().equals("org.eclipse.update.feature"))
							return false;
						if (!r.getId().equals(featureId))
							return false;
						if (!r.getVersion().equals(v2))
							return false;
						return true;
					}
				}

				return false;
			} else {
				return false;
			}
		}

		boolean isFeatureJar(AbstractUnitDelta d) {
			return is(d.left, featureId + ".feature.jar", v1) && is(d.right, featureId + ".feature.jar", v2);
		}

		boolean isFeatureGroup(AbstractUnitDelta d) {
			return is(d.left, featureId + ".feature.group", v1) && is(d.right, featureId + ".feature.group", v2);
		}

		@Override
		void check(Consumer<String> incompatibleChanges) {
		}
	}

	final static private OPathMatcher unitArtifactVersionMatcher = OPathMatcher
			.create("/artifacts/artifact[*]/version");

	final static private OPathMatcher unitTouchpointInstructionValueMatcher = OPathMatcher
			.create("/touchpointData/instructions[*]/instruction[manifest]/value[Bundle-Version]");

	static class RequiredMatcher implements Predicate<Required> {

		Predicate<SearchFilter> filter = p -> p == null;
		Predicate<String> match = p -> p == null;
		Predicate<String> matchParameters = p -> p == null;
		Predicate<Integer> max = p -> p == null;
		Predicate<Integer> min = p -> p == null;
		Predicate<String> name = p -> p == null;
		Predicate<String> namespace = p -> p == null;
		Predicate<VersionRange> range = p -> p == null;

		@Override
		public boolean test(Required t) {
			return filter.test(t.getFilter()) //
					&& match.test(t.getMatch()) //
					&& matchParameters.test(t.getMatchParameters()) //
					&& max.test(t.getMax()) && min.test(t.getMin()) //
					&& name.test(t.getName()) //
					&& namespace.test(t.getNamespace()) //
					&& range.test(t.getRange());
		}

		public RequiredMatcher withNamespace(Predicate<String> namespace) {
			Preconditions.checkNotNull(namespace);
			this.namespace = namespace;
			return this;
		}

		public RequiredMatcher withName(Predicate<String> name) {
			Preconditions.checkNotNull(name);
			this.name = name;
			return this;
		}

		public RequiredMatcher withRange(Predicate<VersionRange> range) {
			Preconditions.checkNotNull(range);
			this.range = range;
			return this;
		}

		public RequiredMatcher withFilter(Predicate<SearchFilter> filter) {
			Preconditions.checkNotNull(range);
			this.filter = filter;
			return this;
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

	class BundleVersionChange extends Change {
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

		Set<Unit> added = new HashSet<>();
		Set<Unit> removed = new HashSet<>();

		@Override
		void check(Consumer<String> incompatibleChanges) {
			for (Unit u : Sets.union(added, removed)) {
				boolean a = added.contains(u);
				boolean r = removed.contains(u);

				if (!a)
					incompatibleChanges.accept("Only removed: " + u + " " + bundleId);
				if (!r)
					incompatibleChanges.accept("Only added: " + u + " " + bundleId);
			}
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

				if (!isBundle(d))
					return false;

				if (isEqual(d.provided, "org.eclipse.equinox.p2.iu", bundleId, v2))
					return true;

				if (isEqual(d.provided, "osgi.bundle", bundleId, v2))
					return true;

				return false;
			} else if (delta instanceof ProvidedRemoved) {
				ProvidedRemoved d = (ProvidedRemoved) delta;

				if (!isBundle(d))
					return false;

				if (isEqual(d.provided, "org.eclipse.equinox.p2.iu", bundleId, v1))
					return true;

				if (isEqual(d.provided, "osgi.bundle", bundleId, v1))
					return true;

				return false;
			} else if (delta instanceof RequiredAdded) {
				RequiredAdded d = (RequiredAdded) delta;

				if (new RequiredMatcher().withNamespace(p -> p.equals("org.eclipse.equinox.p2.iu"))
						.withName(p -> p.equals(bundleId))
						.withRange(p -> p
								.equals(new VersionRange(VersionRange.LEFT_CLOSED, v2, v2, VersionRange.RIGHT_CLOSED)))
						.test(d.required)) {
					added.add(d.right);
					return true;
				}

				return false;
			} else if (delta instanceof RequiredRemoved) {
				RequiredRemoved d = (RequiredRemoved) delta;

				if (new RequiredMatcher().withNamespace(p -> p.equals("org.eclipse.equinox.p2.iu"))
						.withName(p -> p.equals(bundleId))
						.withRange(p -> p
								.equals(new VersionRange(VersionRange.LEFT_CLOSED, v1, v1, VersionRange.RIGHT_CLOSED)))
						.test(d.required)) {
					removed.add(d.right);
					return true;
				}

				return false;
			} else if (delta instanceof UnitDelta) {
				UnitDelta d = (UnitDelta) delta;

				if (!isBundle(d))
					return false;

				if (d.path.getPath().equals("/version")) {
					if (!d.path.getLeft().equals(v1))
						return false;
					if (!d.path.getRight().equals(v2))
						return false;
					return true;
				}

				if (d.path.getPath().equals("/update/range")) {
					return d.path.getLeft()
							.equals(new VersionRange(VersionRange.LEFT_CLOSED, Version.emptyVersion, v1,
									VersionRange.RIGHT_OPEN))
							&& d.path.getRight().equals(new VersionRange(VersionRange.LEFT_CLOSED, Version.emptyVersion,
									v2, VersionRange.RIGHT_OPEN));
				} else if (unitArtifactVersionMatcher.matches(d.path)) {
					MetadataArtifact l = (MetadataArtifact) d.path.getParent().getLeft();
					MetadataArtifact r = (MetadataArtifact) d.path.getParent().getRight();

					if (!l.getClassifier().equals("osgi.bundle"))
						return false;
					if (!l.getId().equals(bundleId))
						return false;
					if (!l.getVersion().equals(v1))
						return false;
					if (!r.getClassifier().equals("osgi.bundle"))
						return false;
					if (!r.getId().equals(bundleId))
						return false;
					if (!r.getVersion().equals(v2))
						return false;
					return true;
				} else if (unitTouchpointInstructionValueMatcher.matches(d.path)) {
					// Instruction l = (Instruction)
					// d.path.getParent().getParent().getLeft();
					// Instruction r = (Instruction)
					// d.path.getParent().getParent().getRight();

					Version vl = VersionParser.valueOf((String) d.path.getLeft());
					Version vr = VersionParser.valueOf((String) d.path.getRight());

					if (!v1.equals(vl))
						return false;
					if (!v2.equals(vr))
						return false;

					return true;
				}
			}
			return false;
		}

		boolean isBundle(AbstractUnitDelta d) {
			return is(d.left, bundleId, v1) && is(d.right, bundleId, v2);
		}

	}

	static String render(Object o) {
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

		if (o instanceof Required || o instanceof Provided || o instanceof Unit) {
			return new DomRenderer().jaxbRender(MetadataRepositoryFactory.getJaxbContext(), o,
					DomRenderer.Options.TOP_LEVEL);
		}

		if (o instanceof ArtifactFacade) {
			return new DomRenderer().jaxbRender(ArtifactRepositoryFactory.getJaxbContext(),
					((ArtifactFacade) o).getData(), DomRenderer.Options.TOP_LEVEL);
		}

		if (o instanceof MetadataArtifact) {
			return new DomRenderer().jaxbRender(MetadataRepositoryFactory.getJaxbContext(), o, "artifact",
					DomRenderer.Options.TOP_LEVEL);
		}

		return String.valueOf(o);
	}
}
