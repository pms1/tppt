package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.IOUtil;

import com.github.pms1.tppt.p2.jaxb.Repository;
import com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.google.common.base.Preconditions;

@Named("default")
@Singleton
public class P2RepositoryFactory {

	@Inject
	private ArtifactRepositoryFactory artifactRepositoryFactory;

	@Inject
	private MetadataRepositoryFactory metadataRepositoryFactory;

	@Inject
	private CompositeArtifactRepositoryFactory compositeArtifactRepositoryFactory;

	@Inject
	private CompositeMetadataRepositoryFactory compositeMetadataRepositoryFactory;

	@Inject
	private Map<String, DataCompression> compressions;

	@Inject
	@Named("xml")
	private DataCompression noCompression;

	public static final String P2INDEX = "p2.index";

	private static final String P2_VERSION = "1";
	private static final String P2_VERSION_PROPERTY = "version";

	private static final Comparator<? super DataCompression> compressionPrioritySorter = (b, a) -> a.getPriority()
			- b.getPriority();

	static class P2Index {
		final private Properties properties;

		P2Index() {
			properties = new Properties();
			properties.put(P2_VERSION_PROPERTY, P2_VERSION);
		}

		P2Index(Path root) throws IOException {
			properties = new Properties();
			try (InputStream is = Files.newInputStream(root.resolve(P2INDEX))) {
				properties.load(is);
			}

			String version = properties.getProperty(P2_VERSION_PROPERTY, null);
			if (!Objects.equals(version, P2_VERSION))
				throw new UnsupportedOperationException(
						"p2.index does not have a supported version: " + version + " at " + root);
		}

		@Deprecated
		public void set(String property, String prefix, DataCompression... compressions) {
			LinkedHashSet<String> entries = new LinkedHashSet<>();

			for (DataCompression c : compressions)
				entries.add(prefix + "." + c.getP2IndexSuffix());
			entries.add("!");

			String newEntry = entries.stream().collect(Collectors.joining(","));

			properties.setProperty(property, newEntry);
		}

		public DataCompression prefered(String property, String prefix, Collection<DataCompression> compressions,
				Set<DataCompression> available) {

			String p2entry = properties.getProperty(property);
			if (p2entry == null)
				throw new IllegalArgumentException("No entry in p2.index for '" + property + "'");
			for (String e : p2entry.trim().split(",")) {
				if (e.equals("!"))
					break;
				if (!e.startsWith(prefix + "."))
					throw new IllegalArgumentException("Unhandled p2.index entry '" + e + "'");
				e = e.substring(prefix.length() + 1);
				for (DataCompression c : compressions) {
					if (c.getP2IndexSuffix().equals(e) && available.contains(c)) {
						return c;
					}
				}
			}

			throw new UnsupportedOperationException("No supported format found in " + p2entry);
		}

		public void write(Path root) throws IOException {
			try (OutputStream os = Files.newOutputStream(root.resolve(P2INDEX))) {
				properties.store(os, null);
			}
		}

		P2IndexType getIndexEntry(P2Kind artifact) {
			String p2entry = properties.getProperty(artifact.getProperty());
			if (p2entry == null)
				throw new IllegalArgumentException("No entry in p2.index for '" + artifact + "'");

			P2IndexType result = null;

			for (String e : p2entry.trim().split(",")) {
				if (e.equals("!"))
					break;

				int idx = e.lastIndexOf('.');
				if (idx == -1)
					throw new IllegalArgumentException();

				P2IndexType t = P2IndexType.fromFilePrefix(e.substring(0, idx));
				if (result == null)
					result = t;
				else if (t != null)
					throw new IllegalArgumentException("Mixed types for '" + artifact + "'");
			}

			if (result == null)
				throw new IllegalArgumentException("No types for '" + artifact + "'");

			return result;
		}

		public P2Type identifyType(Set<P2Kind> kinds) {
			P2Type type = null;

			for (P2Kind t : kinds) {
				P2IndexType entry = getIndexEntry(t);
				if (entry.getKind() != t)
					throw new IllegalArgumentException();
				if (type == null)
					type = entry.getType();
				else if (type != entry.getType())
					throw new IllegalArgumentException();
			}

			if (type == null)
				throw new Error();

			return type;
		}

		@Deprecated
		public String getProperty(String p2MetadataProperty) {
			return properties.getProperty(p2MetadataProperty);
		}

		List<DataCompression> getPossibleCompressions(P2Kind kind, Map<String, DataCompression> compressions) {
			List<DataCompression> result = new ArrayList<>(compressions.size());

			for (String e : properties.getProperty(kind.getProperty()).split(",")) {
				if (e.equals("!"))
					break;

				int idx = e.lastIndexOf('.');
				if (idx == -1)
					throw new IllegalArgumentException();

				String suffix = e.substring(idx + 1);

				DataCompression[] any = compressions.values().stream().filter(p -> p.getP2IndexSuffix().equals(suffix))
						.sorted(compressionPrioritySorter).toArray(DataCompression[]::new);
				if (any.length == 0)
					throw new IllegalArgumentException("No compression known for entry '" + e + "'");

				Collections.addAll(result, any);
			}

			return result;
		}

		List<String> getSuffixes(P2Kind kind) {
			List<String> result = new ArrayList<>(2);

			for (String e : properties.getProperty(kind.getProperty()).split(",")) {
				if (e.equals("!"))
					break;

				int idx = e.lastIndexOf('.');
				if (idx == -1)
					throw new IllegalArgumentException();

				String suffix = e.substring(idx + 1);

				result.add(suffix);
			}

			return result;
		}
	}

	public P2Repository loadContent(Path path, P2Kind... kinds) throws IOException {
		return (P2Repository) loadInternal(path, P2Type.content, parseKinds(kinds));
	}

	class P2RepositoryImpl extends
			AbstractRepository<ArtifactRepository, ArtifactRepositoryFacade, MetadataRepository, MetadataRepositoryFacade>
			implements P2Repository {

		public P2RepositoryImpl(Path path, P2Index p2index, Map<P2Kind, List<DataCompression>> availableCompressions) {
			super(P2Type.content, path, p2index, availableCompressions, artifactRepositoryFactory,
					metadataRepositoryFactory, ArtifactRepositoryFacadeImpl::new, MetadataRepositoryFacadeImpl::new,
					null, null);
		}

		public P2RepositoryImpl(Path path, P2Index p2index, Map<P2Kind, List<DataCompression>> availableCompressions,
				ArtifactRepositoryFacade a, MetadataRepositoryFacade m) {
			super(P2Type.content, path, p2index, availableCompressions, artifactRepositoryFactory,
					metadataRepositoryFactory, ArtifactRepositoryFacadeImpl::new, MetadataRepositoryFacadeImpl::new, a,
					m);
		}

		@Override
		public <T> T accept(P2RepositoryVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	private P2Repository loadContent(Path root, P2Index p2index,
			Map<P2Kind, List<DataCompression>> availableCompressions) throws IOException {

		return new P2RepositoryImpl(root, p2index, availableCompressions);
	}

	static abstract class AbstractRepository<A1 extends Repository, A extends RepositoryFacade<A1>, M1 extends Repository, M extends RepositoryFacade<M1>>
			implements CommonP2Repository {
		private final Path root;
		private final P2Index p2index;
		private final P2IndexType artifact;
		private final P2IndexType metadata;
		private final Map<P2Kind, List<DataCompression>> availableCompressions;

		private final AbstractRepositoryFactory<A1> artifactFactory;
		private final AbstractRepositoryFactory<M1> metadataFactory;

		private final BiFunction<Path, A1, A> artifactCreator;
		private final BiFunction<Path, M1, M> metadataCreator;

		private A a;
		private M m;

		protected AbstractRepository(P2Type type, Path root, P2Index p2index,
				Map<P2Kind, List<DataCompression>> availableCompressions, AbstractRepositoryFactory<A1> aFactory,
				AbstractRepositoryFactory<M1> mFactory, BiFunction<Path, A1, A> aCreator,
				BiFunction<Path, M1, M> mCreator, A a, M m) {
			Objects.requireNonNull(type);
			this.artifact = type.getIndexEntry(P2Kind.artifact);
			this.metadata = type.getIndexEntry(P2Kind.metadata);
			Objects.requireNonNull(root);
			this.root = root;
			Objects.requireNonNull(p2index);
			this.p2index = p2index;
			Objects.requireNonNull(availableCompressions);
			Preconditions.checkArgument(!availableCompressions.isEmpty());
			this.availableCompressions = availableCompressions;
			Objects.requireNonNull(aFactory);
			this.artifactFactory = aFactory;
			Objects.requireNonNull(mFactory);
			this.metadataFactory = mFactory;
			Objects.requireNonNull(aCreator);
			this.artifactCreator = aCreator;
			Objects.requireNonNull(mCreator);
			this.metadataCreator = mCreator;
			this.a = a;
			this.m = m;
		}

		@Override
		final public A getArtifactRepositoryFacade() throws IOException {
			if (a == null) {
				DataCompression dc = availableCompressions.get(P2Kind.artifact).get(0);
				try (InputStream is = dc.openInputStream(root, artifact.getFilePrefix())) {
					a = artifactCreator.apply(root.resolve(artifact.getFilePrefix() + "." + dc.getFileSuffix()),
							artifactFactory.read(is));
				} catch (RuntimeException e) {
					throw new RuntimeException("While loading artifact data from '" + root + "' using " + dc, e);
				}
			}
			return a;
		}

		@Override
		final public M getMetadataRepositoryFacade() throws IOException {
			if (m == null) {
				DataCompression dc = availableCompressions.get(P2Kind.metadata).get(0);
				try (InputStream is = dc.openInputStream(root, metadata.getFilePrefix())) {
					m = metadataCreator.apply(root.resolve(metadata.getFilePrefix() + "." + dc.getFileSuffix()),
							metadataFactory.read(is));
				} catch (RuntimeException e) {
					throw new RuntimeException("While loading metadata from '" + root + "' using " + dc, e);
				}
			}
			return m;
		}

		@Override
		final public List<DataCompression> getMetadataDataCompressions() {
			return availableCompressions.get(P2Kind.metadata);
		}

		@Override
		final public List<DataCompression> getArtifactDataCompressions() {
			return availableCompressions.get(P2Kind.artifact);
		}

		@Override
		public void setCompression(DataCompression... compressions) throws IOException {
			Preconditions.checkNotNull(compressions);
			Preconditions.checkArgument(compressions.length > 0);
			if (kinds().contains(P2Kind.artifact))
				Preconditions.checkState(!availableCompressions.get(P2Kind.artifact).isEmpty());
			if (kinds().contains(P2Kind.metadata))
				Preconditions.checkState(!availableCompressions.get(P2Kind.metadata).isEmpty());
			boolean deleteOldArtifact = kinds().contains(P2Kind.artifact);
			boolean deleteOldMetadata = kinds().contains(P2Kind.metadata);

			for (DataCompression c : compressions) {
				if (kinds().contains(P2Kind.artifact))
					if (c == availableCompressions.get(P2Kind.artifact).get(0))
						deleteOldArtifact = false;
					else
						try (InputStream is = availableCompressions.get(P2Kind.artifact).get(0).openInputStream(root,
								artifact.getFilePrefix())) {
							try (OutputStream os = c.openOutputStream(root, artifact.getFilePrefix())) {
								IOUtil.copy(is, os);
							}
						}

				if (kinds().contains(P2Kind.metadata))
					if (c == availableCompressions.get(P2Kind.metadata).get(0))
						deleteOldMetadata = false;
					else
						try (InputStream is = availableCompressions.get(P2Kind.metadata).get(0).openInputStream(root,
								metadata.getFilePrefix())) {
							try (OutputStream os = c.openOutputStream(root, metadata.getFilePrefix())) {
								IOUtil.copy(is, os);
							}
						}
			}

			if (deleteOldArtifact)
				Files.delete(root.resolve(artifact.getFilePrefix() + "."
						+ availableCompressions.get(P2Kind.artifact).get(0).getFileSuffix()));

			if (deleteOldMetadata)
				Files.delete(root.resolve(metadata.getFilePrefix() + "."
						+ availableCompressions.get(P2Kind.metadata).get(0).getFileSuffix()));

			if (kinds().contains(P2Kind.artifact))
				p2index.set(P2Kind.artifact.getProperty(), artifact.getFilePrefix(), compressions);
			if (kinds().contains(P2Kind.metadata))
				p2index.set(P2Kind.metadata.getProperty(), metadata.getFilePrefix(), compressions);
			p2index.write(root);

			for (P2Kind kind : kinds())
				availableCompressions.put(kind, Arrays.asList(compressions));
		}

		@Override
		public Path getPath() {
			return root;
		}

		private Set<P2Kind> kinds() {
			return availableCompressions.keySet();
		}

		public final void save(DataCompression... compressions) throws IOException {
			for (P2Kind kind : kinds()) {
				if (compressions.length != 0 && !availableCompressions.get(kind).isEmpty())
					throw new IllegalStateException(
							"compressions=" + Arrays.toString(compressions) + " available=" + availableCompressions);

				if (compressions.length == 0 && availableCompressions.get(kind).isEmpty())
					throw new IllegalStateException(
							"compressions=" + Arrays.toString(compressions) + " available=" + availableCompressions);

				if (compressions.length != 0)
					availableCompressions.put(kind, Arrays.asList(compressions));
			}

			if (a != null)
				if (kinds().contains(P2Kind.artifact))
					for (DataCompression c : availableCompressions.get(P2Kind.artifact)) {
						p2index.set(P2RepositoryFactory.P2Kind.artifact.getProperty(), artifact.getFilePrefix(), c);
						try (OutputStream outputStream = c.openOutputStream(root, artifact.getFilePrefix())) {
							artifactFactory.write(a.getRepository(), outputStream);
						}
					}

			if (m != null)
				if (kinds().contains(P2Kind.metadata))
					for (DataCompression c : availableCompressions.get(P2Kind.metadata)) {
						p2index.set(P2RepositoryFactory.P2Kind.metadata.getProperty(), metadata.getFilePrefix(), c);
						try (OutputStream outputStream = c.openOutputStream(root, metadata.getFilePrefix())) {
							metadataFactory.write(m.getRepository(), outputStream);
						}
					}

			if (a != null || m != null)
				p2index.write(root);
		}

	}

	class P2CompositeRepositoryImpl extends
			AbstractRepository<CompositeRepository, CompositeRepositoryFacade, CompositeRepository, CompositeRepositoryFacade>
			implements P2CompositeRepository {

		public P2CompositeRepositoryImpl(Path root, P2Index p2index,
				Map<P2Kind, List<DataCompression>> availableCompressions) {
			super(P2Type.composite, root, p2index, availableCompressions, compositeArtifactRepositoryFactory,
					compositeMetadataRepositoryFactory, CompositeRepositoryFacadeImpl::new,
					CompositeRepositoryFacadeImpl::new, null, null);
		}

		public P2CompositeRepositoryImpl(Path root, P2Index p2index,
				Map<P2Kind, List<DataCompression>> availableCompressions, CompositeRepositoryFacade a,
				CompositeRepositoryFacade m) {
			super(P2Type.composite, root, p2index, availableCompressions, compositeArtifactRepositoryFactory,
					compositeMetadataRepositoryFactory, CompositeRepositoryFacadeImpl::new,
					CompositeRepositoryFacadeImpl::new, a, m);
		}

		@Override
		public <T> T accept(P2RepositoryVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	public P2CompositeRepository createComposite(Path path) throws IOException {
		P2Index p2index = new P2Index();

		Map<P2Kind, List<DataCompression>> availableCompressions = new HashMap<>();
		availableCompressions.put(P2Kind.artifact, Collections.emptyList());
		availableCompressions.put(P2Kind.metadata, Collections.emptyList());

		return new P2CompositeRepositoryImpl(path, p2index, availableCompressions,
				new CompositeRepositoryFacadeImpl(path, compositeArtifactRepositoryFactory.createEmpty()),
				new CompositeRepositoryFacadeImpl(path, compositeMetadataRepositoryFactory.createEmpty()));
	}

	static Set<P2Kind> parseKinds(P2Kind... kind) {
		Preconditions.checkNotNull(kind);
		if (kind.length == 0) {
			return EnumSet.allOf(P2Kind.class);
		} else {
			Set<P2Kind> result = new HashSet<P2Kind>();
			Collections.addAll(result, kind);
			if (kind.length != result.size())
				throw new IllegalArgumentException();
			return result;
		}
	}

	public P2Repository createContent(Path path, P2Kind... kind) throws IOException {
		Set<P2Kind> kinds = parseKinds(kind);
		P2Index p2index = new P2Index();

		Map<P2Kind, List<DataCompression>> availableCompressions = new HashMap<>();
		for (P2Kind k : kinds)
			availableCompressions.put(k, Collections.emptyList());

		return new P2RepositoryImpl(path, p2index, availableCompressions,
				kinds.contains(P2Kind.artifact)
						? new ArtifactRepositoryFacadeImpl(path, artifactRepositoryFactory.createEmpty())
						: null,
				kinds.contains(P2Kind.metadata)
						? new MetadataRepositoryFacadeImpl(path, metadataRepositoryFactory.createEmpty())
						: null);
	}

	public P2CompositeRepository loadComposite(Path path, P2Kind... kinds) throws IOException {
		return (P2CompositeRepository) loadInternal(path, P2Type.composite, parseKinds(kinds));
	}

	private P2CompositeRepository loadComposite(Path path, P2Index p2index,
			Map<P2Kind, List<DataCompression>> availableCompressions) throws IOException {

		return new P2CompositeRepositoryImpl(path, p2index, availableCompressions);
	}

	public enum P2IndexType {
		compositeArtifacts(P2Kind.artifact, P2Type.composite, "compositeArtifacts"), //
		compositeMetadata(P2Kind.metadata, P2Type.composite, "compositeContent"), //
		artifacts(P2Kind.artifact, P2Type.content, "artifacts"), //
		metadata(P2Kind.metadata, P2Type.content, "content");

		private final P2Kind kind;
		private final P2Type type;
		private final String filePrefix;

		private P2IndexType(P2Kind key, P2Type type, String filePrefix) {
			Objects.requireNonNull(key);
			Objects.requireNonNull(type);
			Objects.requireNonNull(filePrefix);
			this.kind = key;
			this.type = type;
			this.filePrefix = filePrefix;
		}

		P2Kind getKind() {
			return kind;
		}

		P2Type getType() {
			return type;
		}

		public String getFilePrefix() {
			return filePrefix;
		}

		static P2IndexType fromFilePrefix(String filePrefix) {
			Objects.requireNonNull(filePrefix);

			for (P2IndexType v : values())
				if (v.getFilePrefix().equals(filePrefix))
					return v;
			throw new IllegalArgumentException("No enum constant has filePrefix '" + filePrefix + "'");
		}
	}

	enum P2Type {
		composite, //
		content;

		public P2IndexType getIndexEntry(P2Kind d) {
			for (P2IndexType e : P2IndexType.values())
				if (e.getKind() == d && e.getType() == this)
					return e;
			throw new UnsupportedOperationException();
		}
	}

	public enum P2Kind {
		artifact, metadata;

		public String getProperty() {
			return name() + ".repository.factory.order";
		}
	}

	public CommonP2Repository loadAny(Path path, P2Kind... kinds) throws IOException {
		return loadInternal(path, null, parseKinds(kinds));
	}

	private CommonP2Repository loadInternal(Path path, P2Type expectedType, Set<P2Kind> kinds) throws IOException {
		P2Index p2index;
		try {
			p2index = new P2Index(path);
		} catch (NoSuchFileException e) {
			throw new NoRepositoryFoundException("No repository with p2.index found at " + path, e);
		}

		P2Type type = p2index.identifyType(kinds);
		if (expectedType != null && type != expectedType)
			throw new IllegalArgumentException(
					"Wrong repository type found at " + path + ". Expected " + expectedType + ", got " + type);

		Map<P2Kind, List<DataCompression>> availableCompressions = new HashMap<>();
		for (P2Kind kind : kinds) {
			List<DataCompression> compr = new ArrayList<>();
			for (String suffix : p2index.getSuffixes(kind)) {
				DataCompression[] all = compressions.values().stream().filter(p -> p.getP2IndexSuffix().equals(suffix))
						.sorted(compressionPrioritySorter).toArray(DataCompression[]::new);
				if (all.length == 0)
					throw new IllegalArgumentException();
				boolean any = false;
				for (DataCompression dc : all) {
					P2IndexType indexType = type.getIndexEntry(kind);

					Path p = path.resolve(indexType.getFilePrefix() + "." + dc.getFileSuffix());
					if (Files.exists(p)) {
						compr.add(dc);
						any = true;
					}
				}
				if (!any)
					throw new IllegalArgumentException();
			}
			// FIXME: check reverse that all files in the directory are covered
			availableCompressions.put(kind, Collections.unmodifiableList(compr));
		}

		switch (type) {
		case composite:
			return loadComposite(path, p2index, availableCompressions);
		case content:
			return loadContent(path, p2index, availableCompressions);
		default:
			throw new UnsupportedOperationException();
		}
	}

	public static class NoRepositoryFoundException extends RuntimeException {
		public NoRepositoryFoundException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
