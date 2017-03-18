package com.github.pms1.tppt.p2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
import com.github.pms1.tppt.p2.P2RepositoryFactory.P2Kind;
import com.github.pms1.tppt.p2.jaxb.artifact.Artifact;
import com.github.pms1.tppt.p2.jaxb.composite.Child;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Instruction;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataArtifact;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Property;
import com.github.pms1.tppt.p2.jaxb.metadata.Provided;
import com.github.pms1.tppt.p2.jaxb.metadata.Required;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
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

		AbstractUnitDelta(FileId id1, Unit left, FileId id2, Unit right, String message, Object... parameters) {
			super(id1, id2, message, parameters);
			Preconditions.checkNotNull(left);
			this.left = left;
			Preconditions.checkNotNull(right);
			this.right = right;
		}
	}

	static abstract class AbstractArtifactDelta extends FileDelta {
		protected final Artifact left;
		protected final Artifact right;

		AbstractArtifactDelta(FileId id1, Artifact left, FileId id2, Artifact right, String message,
				Object... parameters) {
			super(id1, id2, message, parameters);
			Preconditions.checkNotNull(left);
			this.left = left;
			Preconditions.checkNotNull(right);
			this.right = right;
		}
	}

	static class UnitDelta extends AbstractUnitDelta {
		final OPath2 path;

		UnitDelta(FileId id1, Unit left, FileId id2, Unit right, OPath2 path) {
			super(id1, left, id2, right, "Unit changed {0} -> {1}: {2}: {3} -> {4}", left, right, path.getPath(),
					path.getLeft(), path.getRight());
			this.path = path;
		}
	}

	static class ArtifactDelta extends AbstractArtifactDelta {
		final OPath2 path;

		ArtifactDelta(FileId id1, Artifact left, FileId id2, Artifact right, OPath2 path) {
			super(id1, left, id2, right, "Artifact changed {0} -> {1}: {2}: {3} -> {4}", left, right, path.getPath(),
					path.getLeft(), path.getRight());
			this.path = path;
		}
	}

	static class ProvidedAdded extends AbstractUnitDelta {
		final Provided provided;

		ProvidedAdded(FileId id1, Unit left, FileId id2, Unit right, Provided provided) {
			super(id1, left, id2, right, "Provided added: {0} -> {1}: {2}", left, right, provided);
			Preconditions.checkNotNull(provided);
			this.provided = provided;
		}

	}

	static class RequiredAdded extends AbstractUnitDelta {
		final Required required;

		RequiredAdded(FileId id1, Unit left, FileId id2, Unit right, Required required) {
			super(id1, left, id2, right, "Required added: {0} -> {1}: {2}", left, right, required);
			Preconditions.checkNotNull(required);
			this.required = required;
		}

	}

	static class ProvidedRemoved extends AbstractUnitDelta {
		final Provided provided;

		ProvidedRemoved(FileId id1, Unit left, FileId id2, Unit right, Provided provided) {
			super(id1, left, id2, right, "Provided removed: {0} -> {1}: {2}", left, right, provided);
			Preconditions.checkNotNull(provided);
			this.provided = provided;
		}
	}

	static class RequiredRemoved extends AbstractUnitDelta {
		final Required required;

		RequiredRemoved(FileId id1, Unit left, FileId id2, Unit right, Required required) {
			super(id1, left, id2, right, "Required removed: {0} -> {1}: {2}", left, right, required);

			Preconditions.checkNotNull(required);
			this.required = required;
		}
	}

	static class RepositoryAdded extends FileDelta {
		RepositoryAdded(FileId id1, Child left, FileId id2, Child right) {
			super(id1, id2, "Repository added: {0}", right);
		}

	}

	static class RepositoryRemoved extends FileDelta {
		RepositoryRemoved(FileId id1, Child left, FileId id2, Child right) {
			super(id1, id2, "Repository removed: {0}", left);
		}

	}

	static final SearchFilterPrinter printer = new SearchFilterPrinter();

	static ObjectComparatorBuilder<ObjectDelta> createArtifactComparator() {
		return ObjectComparatorBuilder.newBuilder() //
				.addDecomposer("//artifacts/artifact", new Decomposer<List<Artifact>>() {

					DecomposedObject dc1(List<Artifact> units, Function<Artifact, String> f) {
						DecomposedObject r = new DecomposedObject();

						for (Artifact u : units) {
							if (!r.put(OPath.index(f.apply(u)), u))
								return null;
						}

						return r;
					}

					@Override
					public DecomposedObject decompose(List<Artifact> o) {
						DecomposedObject r;

						r = dc1(o, p -> p.getId());
						if (r != null)
							return r;

						r = dc1(o, p -> p.getId() + "/" + p.getVersion());
						if (r != null)
							return r;

						throw new Error();
					}

				}) //
				.addDecomposer("//artifacts/artifact[*]/properties/property",
						ObjectComparator.<com.github.pms1.tppt.p2.jaxb.artifact.Property>listToMapDecomposer(
								p -> p.getName())) //
				.addDecomposer("//properties/property",
						ObjectComparator.<com.github.pms1.tppt.p2.jaxb.artifact.Property>listToMapDecomposer(
								p -> p.getName()));
	}

	static ObjectComparatorBuilder<ObjectDelta> createMetadataComparator() {
		return ObjectComparatorBuilder.newBuilder()
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
											new ByteArrayInputStream(value.trim().getBytes(StandardCharsets.UTF_8)),
											null);

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
				// ObjectComparator.<Unit>listToMapDecomposer(p -> p.getId() +
				// "/" +
				// p.getVersion()))
				.addDecomposer("//units/unit[*]/touchpointData/instructions[*]/instruction",
						ObjectComparator.<Instruction>listToMapDecomposer(p -> p.getKey()))
				.addDecomposer("//units/unit[*]/artifacts/artifact",
						ObjectComparator
								.<MetadataArtifact>listToMapDecomposer(p -> p.getId() + "/" + p.getClassifier()))
				.addDecomposer("//properties/property",
						ObjectComparator.<Property>listToMapDecomposer(p -> p.getName()));
	}

	static class MetadataDelta extends FileDelta {
		private final OPath2 path;
		private final ChangeType change;

		public MetadataDelta(FileId id1, FileId id2, OPath2 p, ChangeType change) {
			super(id1, id2, "Metadata change {0} {1}: {2} -> {3}", p.getPath(), change, p.getLeft(), p.getRight());
			this.path = p;
			this.change = change;
		}
	}

	static class ArtifactsDelta extends FileDelta {
		private final OPath2 path;
		private final ChangeType change;

		public ArtifactsDelta(FileId id1, FileId id2, OPath2 p, ChangeType change) {
			super(id1, id2, "Artifacts change {0} {1}: {2} -> {3}", p.getPath(), change, p.getLeft(), p.getRight());
			this.path = p;
			this.change = change;
		}
	}

	private void compare(FileId root1, List<DataCompression> s1, FileId root2, List<DataCompression> s2, P2Kind prefix,
			Consumer<FileDelta> dest) {

		if (!Objects.equals(s1, s2))
			dest.accept(new RepositoryDataCompressionChanged(root1, root2, prefix, s1, s2));
	}

	@SuppressWarnings("unchecked")
	public final boolean run(P2Repository pr1, P2Repository pr2) throws IOException {
		return run(pr1, pr2, new Supplier[0]);
	}

	@SafeVarargs
	public final boolean run(CommonP2Repository pr1, CommonP2Repository pr2, Supplier<Change>... acceptedChanges)
			throws IOException {
		if (pr1.getClass() != pr2.getClass()) {
			// FIXME: path
			logger.info("Repository type changed");
			return false;
		}

		return pr1.accept(new P2RepositoryVisitor<Boolean>() {

			@Override
			public Boolean visit(P2CompositeRepository repo) {
				try {
					return run((P2CompositeRepository) pr1, (P2CompositeRepository) pr2, acceptedChanges);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public Boolean visit(P2Repository repo) {
				try {
					return run((P2Repository) pr1, (P2Repository) pr2, acceptedChanges);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	private boolean run(P2CompositeRepository pr1, P2CompositeRepository pr2,
			@SuppressWarnings("unchecked") Supplier<Change>... acceptedChanges) throws IOException {
		List<FileDelta> dest = new ArrayList<>();

		FileId root1 = FileId.newRoot(pr1.getPath());
		FileId root2 = FileId.newRoot(pr2.getPath());

		compare(root1, pr1.getArtifactDataCompressions(), root2, pr2.getArtifactDataCompressions(), P2Kind.artifact,
				dest::add);
		compare(root1, pr1.getMetadataDataCompressions(), root2, pr2.getMetadataDataCompressions(), P2Kind.metadata,
				dest::add);

		CompositeRepositoryFacade arf1 = pr1.getArtifactRepositoryFacade();
		FileId arf1id = FileId.newRoot(arf1.getPath());
		CompositeRepositoryFacade arf2 = pr2.getArtifactRepositoryFacade();
		FileId arf2id = FileId.newRoot(arf2.getPath());

		CompositeRepository ar1 = arf1.getRepository();
		CompositeRepository ar2 = arf2.getRepository();
		// MetadataRepositoryFacade mdf1 = pr1.getMetadataRepositoryFacade();
		// FileId mdf1id = FileId.newRoot(mdf1.getPath());
		// MetadataRepositoryFacade mdf2 = pr2.getMetadataRepositoryFacade();
		// FileId mdf2id = FileId.newRoot(mdf2.getPath());
		//
		// MetadataRepository md1 = mdf1.getRepository();
		// MetadataRepository md2 = mdf2.getRepository();
		//
		// ArtifactRepositoryFacade r1 = pr1.getArtifactRepositoryFacade();
		// FileId r1id = FileId.newRoot(r1.getPath());
		// ArtifactRepositoryFacade r2 = pr2.getArtifactRepositoryFacade();
		// FileId r2id = FileId.newRoot(r2.getPath());

		ObjectComparator<FileDelta> oc = ObjectComparatorBuilder.newBuilder() //
				.addDecomposer("//properties/property",
						ObjectComparator.<com.github.pms1.tppt.p2.jaxb.composite.Property>listToMapDecomposer(
								com.github.pms1.tppt.p2.jaxb.composite.Property::getName)) //
				.addDecomposer("//children/child", ObjectComparator.<Child>listToMapDecomposer(Child::getLocation)) //
				.setDeltaCreator(new DeltaCreator<FileDelta>() {

					@Override
					public FileDelta changed(OPath2 p, ChangeType change, Object m1, Object m2) {
						if (p.size() > 3) {
							OPath2 unitPath = p.subPath(0, 3);
							if (unitPath.getPath().equals("//children/child")) {
								switch (change) {
								case ADDED:
									return new RepositoryAdded(arf1id, (Child) m1, arf2id, (Child) m2);
								case REMOVED:
									return new RepositoryRemoved(arf1id, (Child) m1, arf2id, (Child) m2);
								default:
									throw new Error(p + " " + change + " " + m1 + " " + m2);
								}
							}
						}
						// FIXME: here?
						switch (p.getPath()) {
						case "//properties/property[p2.timestamp]/value":
							return null;
						}

						throw new Error(p + " " + change + " " + m1 + " " + m2);
					}

				}).build();

		dest.addAll(oc.compare(ar1, ar2));

		List<String> incompatibleChanges = new ArrayList<>();

		for (FileDelta d : dest) {
			// if (!changes.stream().anyMatch(c -> c.accept(d)))
			incompatibleChanges.add(render(d));
		}

		// for (Change c : changes)
		// c.check(incompatibleChanges::add);

		incompatibleChanges.forEach(p -> logger.info("Incompatible change: " + p));

		return incompatibleChanges.isEmpty();
	}

	private void compareArtifacts(ArtifactRepositoryFacade r1, FileId r1id, ArtifactFacade a1,
			ArtifactRepositoryFacade r2, FileId r2id, ArtifactFacade a2, List<FileDelta> dest, List<Change> changes)
			throws IOException {

		Path p1 = r1.getArtifactUri(a1.getId());
		Path p2 = r2.getArtifactUri(a2.getId());

		String classifier1 = a1.getClassifier();
		String classifier2 = a2.getClassifier();
		if (!classifier1.equals(classifier2)) {
			dest.add(new ArtifactClassifierDelta(r1id, r2id, a1.getId().getId(), render(a1), render(a2)));
			return;
		}

		FileId file1 = FileId.newRoot(p1);
		FileId file2 = FileId.newRoot(p2);

		FileComparator comparator = null;
		switch (classifier1) {
		case "osgi.bundle":
			comparator = comparators.get(BundleComparator.HINT);
			changes.add(new BundleVersionChange(a1.getId().getId(), file1, a1.getId().getVersion(), file2,
					a2.getId().getVersion()));
			break;
		case "org.eclipse.update.feature":
			comparator = comparators.get(FeatureComparator.HINT);
			changes.add(new FeatureVersionChange(a1.getId().getId(), file1, a1.getId().getVersion(), file2,
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

	@SafeVarargs
	public final boolean run(P2Repository pr1, P2Repository pr2, Supplier<Change>... acceptedChanges)
			throws IOException {
		List<FileDelta> dest = new ArrayList<>();

		FileId root1 = FileId.newRoot(pr1.getPath());
		FileId root2 = FileId.newRoot(pr2.getPath());

		compare(root1, pr1.getArtifactDataCompressions(), root2, pr2.getArtifactDataCompressions(), P2Kind.artifact,
				dest::add);
		compare(root1, pr1.getMetadataDataCompressions(), root2, pr2.getMetadataDataCompressions(), P2Kind.metadata,
				dest::add);

		MetadataRepositoryFacade mdf1 = pr1.getMetadataRepositoryFacade();
		FileId mdf1id = FileId.newRoot(mdf1.getPath());
		MetadataRepositoryFacade mdf2 = pr2.getMetadataRepositoryFacade();
		FileId mdf2id = FileId.newRoot(mdf2.getPath());

		MetadataRepository md1 = mdf1.getRepository();
		MetadataRepository md2 = mdf2.getRepository();

		ArtifactRepositoryFacade r1 = pr1.getArtifactRepositoryFacade();
		FileId r1id = FileId.newRoot(r1.getPath());
		ArtifactRepositoryFacade r2 = pr2.getArtifactRepositoryFacade();
		FileId r2id = FileId.newRoot(r2.getPath());

		List<Change> changes = new LinkedList<>();
		for (Supplier<Change> a : acceptedChanges)
			changes.add(a.get());

		ObjectComparator<FileDelta> oc = createMetadataComparator().setDeltaCreator(new DeltaCreator<FileDelta>() {

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
									throw new Error(change + " " + rel.getPath());
								}
							case "/requires/required":
								switch (change) {
								case ADDED:
									return new RequiredAdded(mdf1id, uleft, mdf2id, uright, (Required) m2);
								case REMOVED:
									return new RequiredRemoved(mdf1id, uleft, mdf2id, uright, (Required) m1);
								default:
									throw new Error(change + " " + rel.getPath());
								}
							}

							switch (change) {
							case ADDED:
							case REMOVED:
							case CHANGED:
								return new UnitDelta(mdf1id, uleft, mdf2id, uright, rel);
							default:
								throw new Error(change + " " + rel.getPath());
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

		ObjectComparator<FileDelta> oc2 = createArtifactComparator().setDeltaCreator(new DeltaCreator<FileDelta>() {

			@Override
			public FileDelta changed(OPath2 p, ChangeType change, Object m1, Object m2) {

				if (p.size() > 3) {
					OPath2 unitPath = p.subPath(0, 3);
					if (unitPath.getPath().equals("//artifacts/artifact")) {

						OPath2 rel = p.subPath(4);
						if (rel == null) {
							return new ArtifactsDelta(mdf1id, mdf2id, p, change);
						} else {
							Artifact uleft = (Artifact) p.subPath(3, 4).getLeft();
							Artifact uright = (Artifact) p.subPath(3, 4).getRight();

							switch (change) {
							case ADDED:
							case REMOVED:
							case CHANGED:
								return new ArtifactDelta(mdf1id, uleft, mdf2id, uright, rel);
							default:
								throw new Error(change + " " + rel.getPath());
							}

						}
					}
				}

				// FIXME: here?
				switch (p.getPath()) {
				case "//properties/property[p2.timestamp]/value":
					return null;
				}

				return new ArtifactsDelta(mdf1id, mdf2id, p, change);
			}

		}).build();

		dest.addAll(oc2.compare(pr1.getArtifactRepositoryFacade().getRepository(),
				pr2.getArtifactRepositoryFacade().getRepository()));

		Multimap<String, ArtifactId> id1 = HashMultimap.create();
		Multimap<String, ArtifactId> id2 = HashMultimap.create();

		r1.getArtifacts().values().forEach(a -> id1.put(a.getId().getId(), a.getId()));
		r2.getArtifacts().values().forEach(a -> id2.put(a.getId().getId(), a.getId()));

		for (String a : Sets.union(id1.keySet(), id2.keySet())) {
			Set<ArtifactId> i1 = new HashSet<>(id1.get(a));
			Set<ArtifactId> i2 = new HashSet<>(id2.get(a));

			Set<ArtifactId> intersection = new HashSet<>(i1);
			intersection.retainAll(i2);

			for (ArtifactId id : intersection) {
				compareArtifacts(r1, r1id, r1.getArtifacts().get(id), r2, r2id, r2.getArtifacts().get(id), dest,
						changes);

				i1.remove(id);
				i2.remove(id);
			}

			if (i1.size() == 1 && i2.size() == 1) {
				compareArtifacts(r1, r1id, r1.getArtifacts().get(Iterables.getOnlyElement(i1)), r2, r2id,
						r2.getArtifacts().get(Iterables.getOnlyElement(i2)), dest, changes);
			} else {
				for (ArtifactId id : i1)
					dest.add(new ArtifactRemovedDelta(r1id, r2id, render(r1.getArtifacts().get(id)), id));
				for (ArtifactId id : i2)
					dest.add(new ArtifactAddedDelta(r1id, r2id, id, render(r2.getArtifacts().get(id))));
			}
		}

		List<String> incompatibleChanges = new ArrayList<>();

		for (FileDelta d : dest)
			if (!changes.stream().anyMatch(c -> c.accept(d)))
				incompatibleChanges.add(render(d));

		for (Change c : changes)
			c.check(incompatibleChanges::add);

		incompatibleChanges.forEach(p -> logger.info("Incompatible change: " + p));

		return incompatibleChanges.isEmpty();
	}

	private String render(FileDelta d1) {
		MessageFormat messageFormat = new MessageFormat(d1.getDescription());

		Object[] o1 = d1.getParameters().clone();
		for (int i = o1.length; i-- > 0;)
			o1[i] = render(o1[i]);
		return d1.getBaselineFile() + " -> " + d1.getCurrentFile() + ": " + messageFormat.format(o1);
	}

	public static abstract class Change {
		abstract boolean accept(FileDelta delta);

		abstract void check(Consumer<String> change);
	}

	private static final Predicate<SearchFilter> featureFilter = p -> p != null
			&& printer.print(p).equals("(org.eclipse.update.install.features=true)");

	class FeatureVersionChange extends ArtifactVersionChange {
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
		boolean accept(FileDelta delta) {
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
							.withNamespace("org.eclipse.equinox.p2.iu"::equals)
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
							.withNamespace("org.eclipse.equinox.p2.iu"::equals)
							.withName(p -> p.equals(featureId + ".feature.jar")) //
							.withRange(p -> Objects.equals(p,
									new VersionRange(VersionRange.LEFT_CLOSED, v1, v1, VersionRange.RIGHT_CLOSED))) //
							.withFilter(featureFilter) //
							.test(d.required))
						return true;
				}

				return false;
			} else if (delta instanceof ArtifactDelta) {
				ArtifactDelta d = (ArtifactDelta) delta;

				switch (d.path.getPath()) {
				case "/properties/property[download.md5]/value":
				case "/properties/property[artifact.size]/value":
				case "/properties/property[download.size]/value":
					return true;
				}

				if (isFeatureJar(d)) {
					switch (d.path.getPath()) {
					case "/version":
						if (d.path.getLeft().equals(v1) && d.path.getRight().equals(v2)) {
							removedArtifactsArtifacts.add(ArtifactKey.create(d.left));
							addedArtifactsArtifacts.add(ArtifactKey.create(d.right));
							return true;
						}
					}
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

						removedArtifactsMetadata.add(ArtifactKey.create(l));
						addedArtifactsMetadata.add(ArtifactKey.create(r));
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

		boolean isFeatureJar(ArtifactDelta d) {
			return d.left.getClassifier().equals("org.eclipse.update.feature")
					&& d.right.getClassifier().equals("org.eclipse.update.feature") && is(d.left, featureId, v1)
					&& is(d.right, featureId, v2);
		}

		boolean isFeatureGroup(AbstractUnitDelta d) {
			return is(d.left, featureId + ".feature.group", v1) && is(d.right, featureId + ".feature.group", v2);
		}
	}

	static class ArtifactKey {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classifier == null) ? 0 : classifier.hashCode());
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			result = prime * result + ((version == null) ? 0 : version.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ArtifactKey other = (ArtifactKey) obj;
			if (classifier == null) {
				if (other.classifier != null)
					return false;
			} else if (!classifier.equals(other.classifier))
				return false;
			if (id == null) {
				if (other.id != null)
					return false;
			} else if (!id.equals(other.id))
				return false;
			if (version == null) {
				if (other.version != null)
					return false;
			} else if (!version.equals(other.version))
				return false;
			return true;
		}

		private final String id;
		private final Version version;
		private final String classifier;

		public ArtifactKey(String id, Version version, String classifier) {
			Preconditions.checkNotNull(id);
			Preconditions.checkNotNull(version);
			Preconditions.checkNotNull(classifier);
			this.id = id;
			this.version = version;
			this.classifier = classifier;
		}

		public static ArtifactKey create(Artifact a) {
			return new ArtifactKey(a.getId(), a.getVersion(), a.getClassifier());
		}

		public static ArtifactKey create(MetadataArtifact r) {
			return new ArtifactKey(r.getId(), r.getVersion(), r.getClassifier());
		}

		@Override
		public String toString() {
			return "ArtifactKey(" + id + "," + version + "," + classifier + ")";
		}

	}

	final static private OPathMatcher unitArtifactVersionMatcher = OPathMatcher
			.create("/artifacts/artifact[*]/version");

	final static private OPathMatcher unitTouchpointInstructionValueMatcher = OPathMatcher
			.create("/touchpointData/instructions[*]/instruction[manifest]/value[Bundle-Version]");

	static class ProvidedMatcher implements Predicate<Provided> {
		Predicate<String> name = p -> p == null;
		Predicate<String> namespace = p -> p == null;
		Predicate<Version> version = p -> p == null;

		@Override
		public boolean test(Provided t) {
			return name.test(t.getName()) //
					&& namespace.test(t.getNamespace()) //
					&& version.test(t.getVersion());
		}

		public ProvidedMatcher withNamespace(Predicate<String> namespace) {
			Preconditions.checkNotNull(namespace);
			this.namespace = namespace;
			return this;
		}

		public ProvidedMatcher withName(Predicate<String> name) {
			Preconditions.checkNotNull(name);
			this.name = name;
			return this;
		}

		public ProvidedMatcher withVersion(Predicate<Version> version) {
			Preconditions.checkNotNull(version);
			this.version = version;
			return this;
		}
	}

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

	static boolean is(Artifact u, String id, Version version) {
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

	/**
	 * Common super class for metadata changes that imply version changes in
	 * associated artifacts.
	 * 
	 */
	abstract class ArtifactVersionChange extends Change {

		Set<ArtifactKey> removedArtifactsMetadata = new HashSet<>();
		Set<ArtifactKey> addedArtifactsMetadata = new HashSet<>();
		Set<ArtifactKey> removedArtifactsArtifacts = new HashSet<>();
		Set<ArtifactKey> addedArtifactsArtifacts = new HashSet<>();

		@Override
		void check(Consumer<String> incompatibleChanges) {
			for (ArtifactKey u : Sets.union(removedArtifactsArtifacts, removedArtifactsMetadata)) {
				boolean a = removedArtifactsArtifacts.contains(u);
				boolean m = removedArtifactsMetadata.contains(u);

				if (!a)
					incompatibleChanges.accept("Only removed in metadata: " + u);
				if (!m)
					incompatibleChanges.accept("Only removed in artifacts: " + u);
			}

			for (ArtifactKey u : Sets.union(addedArtifactsArtifacts, addedArtifactsMetadata)) {
				boolean a = addedArtifactsArtifacts.contains(u);
				boolean m = addedArtifactsMetadata.contains(u);

				if (!a)
					incompatibleChanges.accept("Only added in metadata: " + u);
				if (!m)
					incompatibleChanges.accept("Only added in artifacts: " + u);
			}
		}
	}

	class BundleVersionChange extends ArtifactVersionChange {
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

		Set<Unit> addedUnit = new HashSet<>();
		Set<Unit> removedUnit = new HashSet<>();

		Set<String> addedProvidedPackage = new HashSet<>();
		Set<String> removedProvidedPackage = new HashSet<>();

		@Override
		void check(Consumer<String> incompatibleChanges) {
			super.check(incompatibleChanges);

			for (Unit u : Sets.union(addedUnit, removedUnit)) {
				boolean a = addedUnit.contains(u);
				boolean r = removedUnit.contains(u);

				if (!a)
					incompatibleChanges.accept("Only removed: " + u + " " + bundleId);
				if (!r)
					incompatibleChanges.accept("Only added: " + u + " " + bundleId);
			}

			for (String u : Sets.union(addedProvidedPackage, removedProvidedPackage)) {
				boolean a = addedProvidedPackage.contains(u);
				boolean r = removedProvidedPackage.contains(u);

				if (!a)
					incompatibleChanges.accept("Only removed package: " + u + " " + bundleId);
				if (!r)
					incompatibleChanges.accept("Only added package: " + u + " " + bundleId);
			}

			return;
		}

		@Override
		boolean accept(FileDelta delta) {
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
			} else if (delta instanceof ManifestExportPackageVersionDelta) {
				ManifestExportPackageVersionDelta d = (ManifestExportPackageVersionDelta) delta;

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

				if (new ProvidedMatcher().withNamespace("java.package"::equals) //
						.withName(p -> true) //
						.withVersion(v2::equals) //
						.test(d.provided)) {
					addedProvidedPackage.add(d.provided.getName());
					return true;
				}

				return false;
			} else if (delta instanceof ProvidedRemoved) {
				ProvidedRemoved d = (ProvidedRemoved) delta;

				if (!isBundle(d))
					return false;

				if (isEqual(d.provided, "org.eclipse.equinox.p2.iu", bundleId, v1))
					return true;

				if (isEqual(d.provided, "osgi.bundle", bundleId, v1))
					return true;

				if (new ProvidedMatcher().withNamespace("java.package"::equals) //
						.withName(p -> true) //
						.withVersion(v1::equals) //
						.test(d.provided)) {
					removedProvidedPackage.add(d.provided.getName());
					return true;
				}

				return false;
			} else if (delta instanceof RequiredAdded) {
				RequiredAdded d = (RequiredAdded) delta;

				if (new RequiredMatcher().withNamespace("org.eclipse.equinox.p2.iu"::equals)
						.withName(p -> p.equals(bundleId))
						.withRange(p -> p
								.equals(new VersionRange(VersionRange.LEFT_CLOSED, v2, v2, VersionRange.RIGHT_CLOSED)))
						.test(d.required)) {
					addedUnit.add(d.right);
					return true;
				}

				return false;
			} else if (delta instanceof RequiredRemoved) {
				RequiredRemoved d = (RequiredRemoved) delta;

				if (new RequiredMatcher().withNamespace("org.eclipse.equinox.p2.iu"::equals)
						.withName(p -> p.equals(bundleId))
						.withRange(p -> p
								.equals(new VersionRange(VersionRange.LEFT_CLOSED, v1, v1, VersionRange.RIGHT_CLOSED)))
						.test(d.required)) {
					removedUnit.add(d.right);
					return true;
				}

				return false;
			} else if (delta instanceof ArtifactDelta) {
				ArtifactDelta d = (ArtifactDelta) delta;

				switch (d.path.getPath()) {
				case "/properties/property[download.md5]/value":
				case "/properties/property[artifact.size]/value":
				case "/properties/property[download.size]/value":
					return true;
				}

				if (isBundle(d)) {
					switch (d.path.getPath()) {
					case "/version":
						if (d.path.getLeft().equals(v1) && d.path.getRight().equals(v2)) {
							removedArtifactsArtifacts.add(ArtifactKey.create(d.left));
							addedArtifactsArtifacts.add(ArtifactKey.create(d.right));
							return true;
						}
					}
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

					removedArtifactsMetadata.add(ArtifactKey.create(l));
					addedArtifactsMetadata.add(ArtifactKey.create(r));

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

		boolean isBundle(ArtifactDelta d) {
			return is(d.left, bundleId, v1) && is(d.right, bundleId, v2);
		}

	}

	private String render(Object o) {
		if (o instanceof List) {
			StringBuilder b = new StringBuilder();
			b.append("[");
			List<Object> l = (List<Object>) (List<?>) o;
			return "[" + l.stream().map(this::render).collect(Collectors.joining(", ")) + "]";
		}

		if (o instanceof Provided) {
			Provided p = (Provided) o;
			return "Provided(" + p.getNamespace() + "," + p.getName() + "," + p.getVersion() + ")";
		}

		if (o instanceof Child) {
			return new DomRenderer().jaxbRender(CompositeRepositoryFactory.getJaxbContext(), o,
					DomRenderer.Options.TOP_LEVEL);
		}

		if (o instanceof Required || o instanceof Provided || o instanceof Unit) {
			return new DomRenderer().jaxbRender(MetadataRepositoryFactory.getJaxbContext(), o,
					DomRenderer.Options.TOP_LEVEL);
		}

		if (o instanceof ArtifactFacade) {
			o = ((ArtifactFacade) o).getData();
		}

		if (o instanceof Artifact) {
			return new DomRenderer().jaxbRender(ArtifactRepositoryFactory.getJaxbContext(), o,
					DomRenderer.Options.TOP_LEVEL);
		}

		if (o instanceof MetadataArtifact) {
			return new DomRenderer().jaxbRender(MetadataRepositoryFactory.getJaxbContext(), o, "artifact",
					DomRenderer.Options.TOP_LEVEL);
		}

		return String.valueOf(o);
	}
}
