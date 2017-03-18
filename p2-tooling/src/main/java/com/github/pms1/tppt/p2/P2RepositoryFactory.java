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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.IOUtil;

import com.google.common.base.Preconditions;

@Component(role = P2RepositoryFactory.class)
public class P2RepositoryFactory {

	@Requirement
	private ArtifactRepositoryFactory artifactRepositoryFactory;

	@Requirement
	private MetadataRepositoryFactory metadataRepositoryFactory;

	@Requirement
	private CompositeArtifactRepositoryFactory compositeArtifactRepositoryFactory;

	@Requirement
	private CompositeMetadataRepositoryFactory compositeMetadataRepositoryFactory;

	@Requirement
	private Map<String, DataCompression> compressions;

	@Requirement(hint = "xml")
	private DataCompression noCompression;

	void setCompressions(Map<String, DataCompression> compressions) {
		this.compressions = compressions;
	}

	public static final String P2INDEX = "p2.index";
	@Deprecated
	public static final String ARTIFACT_PREFIX = P2IndexType.artifacts.getFilePrefix();
	@Deprecated
	public static final String METADATA_PREFIX = P2IndexType.metadata.getFilePrefix();

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
				else if (result != null)
					throw new IllegalArgumentException("Mixed types for '" + artifact + "'");
			}

			if (result == null)
				throw new IllegalArgumentException("No types for '" + artifact + "'");

			return result;
		}

		public P2Type identifyType() {
			P2Type type = null;

			for (P2Kind t : P2Kind.values()) {
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

	public P2Repository loadContent(Path path) throws IOException {
		return (P2Repository) loadInternal(path, P2Type.content);
	}

	static class P2RepositoryImpl extends AbstractRepository<ArtifactRepositoryFacade, MetadataRepositoryFacade>
			implements P2Repository {

		public P2RepositoryImpl(Path path, P2Index p2index, Map<P2Kind, List<DataCompression>> availableCompressions,
				BiFunction<Path, DataCompression, ArtifactRepositoryFacade> artifactLoader,
				BiFunction<Path, DataCompression, MetadataRepositoryFacade> metadataLoader) {
			super(P2Type.content, path, p2index, availableCompressions, artifactLoader, metadataLoader);
		}

		@Override
		public <T> T accept(P2RepositoryVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	private P2Repository loadContent(Path root, P2Index p2index,
			Map<P2Kind, List<DataCompression>> availableCompressions) throws IOException {

		return new P2RepositoryImpl(root, p2index, availableCompressions, (path, dc) -> {
			try (InputStream is = dc.openInputStream(path, P2IndexType.artifacts.getFilePrefix())) {
				return new ArtifactRepositoryFacadeImpl(
						path.resolve(P2IndexType.artifacts.getFilePrefix() + "." + dc.getFileSuffix()),
						artifactRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, (path, dc) -> {
			try (InputStream is = dc.openInputStream(path, P2IndexType.metadata.getFilePrefix())) {
				return new MetadataRepositoryFacadeImpl(
						path.resolve(P2IndexType.metadata.getFilePrefix() + "." + dc.getFileSuffix()),
						metadataRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	static abstract class AbstractRepository<A extends RepositoryFacade, M extends RepositoryFacade>
			implements CommonP2Repository {
		final Path root;
		final P2Index p2index;
		final P2IndexType artifact;
		final P2IndexType metadata;
		final Map<P2Kind, List<DataCompression>> availableCompressions;

		private final BiFunction<Path, DataCompression, A> artifactLoader;

		private final BiFunction<Path, DataCompression, M> metadataLoader;

		protected AbstractRepository(P2Type type, Path root, P2Index p2index,
				Map<P2Kind, List<DataCompression>> availableCompressions,
				BiFunction<Path, DataCompression, A> artifactLoader,
				BiFunction<Path, DataCompression, M> metadataLoader) {
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
			this.artifactLoader = artifactLoader;
			this.metadataLoader = metadataLoader;
		}

		protected AbstractRepository(P2Type type, Path root, P2Index p2index,
				Map<P2Kind, List<DataCompression>> availableCompressions,
				BiFunction<Path, DataCompression, A> artifactLoader,
				BiFunction<Path, DataCompression, M> metadataLoader, A a, M m) {
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
			this.artifactLoader = artifactLoader;
			this.metadataLoader = metadataLoader;
			this.a = a;
			this.m = m;
		}

		A a;

		@Override
		final public A getArtifactRepositoryFacade() throws IOException {
			if (a == null)
				a = artifactLoader.apply(root, availableCompressions.get(P2Kind.artifact).get(0));
			return a;
		}

		M m;

		@Override
		final public M getMetadataRepositoryFacade() throws IOException {
			if (m == null)
				m = metadataLoader.apply(root, availableCompressions.get(P2Kind.metadata).get(0));
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
			Preconditions.checkState(!availableCompressions.get(P2Kind.artifact).isEmpty());
			Preconditions.checkState(!availableCompressions.get(P2Kind.metadata).isEmpty());
			DataCompression preferredArtifactCompression = availableCompressions.get(P2Kind.artifact).get(0);
			DataCompression preferredMetadataCompression = availableCompressions.get(P2Kind.metadata).get(0);

			boolean deleteOldArtifact = true;
			boolean deleteOldMetadata = true;

			for (DataCompression c : compressions) {
				if (c == preferredArtifactCompression)
					deleteOldArtifact = false;
				else
					try (InputStream is = preferredArtifactCompression.openInputStream(root,
							artifact.getFilePrefix())) {
						try (OutputStream os = c.openOutputStream(root, artifact.getFilePrefix())) {
							IOUtil.copy(is, os);
						}
					}
				if (c == preferredMetadataCompression)
					deleteOldMetadata = false;
				else
					try (InputStream is = preferredMetadataCompression.openInputStream(root,
							metadata.getFilePrefix())) {
						try (OutputStream os = c.openOutputStream(root, metadata.getFilePrefix())) {
							IOUtil.copy(is, os);
						}
					}
			}

			if (deleteOldArtifact)
				Files.delete(
						root.resolve(artifact.getFilePrefix() + "." + preferredArtifactCompression.getFileSuffix()));

			if (deleteOldMetadata)
				Files.delete(
						root.resolve(metadata.getFilePrefix() + "." + preferredMetadataCompression.getFileSuffix()));

			p2index.set(P2Kind.artifact.getProperty(), artifact.getFilePrefix(), compressions);
			p2index.set(P2Kind.metadata.getProperty(), metadata.getFilePrefix(), compressions);
			p2index.write(root);

			availableCompressions.put(P2Kind.artifact, Arrays.asList(compressions));
			availableCompressions.put(P2Kind.metadata, Arrays.asList(compressions));
		}

		@Override
		public Path getPath() {
			return root;
		}
	}

	class P2CompositeRepositoryImpl extends AbstractRepository<CompositeRepositoryFacade, CompositeRepositoryFacade>
			implements P2CompositeRepository {

		public P2CompositeRepositoryImpl(Path root, P2Index p2index,
				Map<P2Kind, List<DataCompression>> availableCompressions,
				BiFunction<Path, DataCompression, CompositeRepositoryFacade> artifactLoader,
				BiFunction<Path, DataCompression, CompositeRepositoryFacade> metadataLoader) {
			super(P2Type.composite, root, p2index, availableCompressions, artifactLoader, metadataLoader);
		}

		public P2CompositeRepositoryImpl(Path root, P2Index p2index,
				Map<P2Kind, List<DataCompression>> availableCompressions,
				BiFunction<Path, DataCompression, CompositeRepositoryFacade> artifactLoader,
				BiFunction<Path, DataCompression, CompositeRepositoryFacade> metadataLoader,
				CompositeRepositoryFacade a, CompositeRepositoryFacade m) {
			super(P2Type.composite, root, p2index, availableCompressions, artifactLoader, metadataLoader, a, m);
		}

		@Override
		public void save() throws IOException {
			p2index.set(P2RepositoryFactory.P2Kind.artifact.getProperty(), artifact.getFilePrefix(), noCompression);
			p2index.set(P2RepositoryFactory.P2Kind.metadata.getProperty(), metadata.getFilePrefix(), noCompression);
			p2index.write(root);
			try (OutputStream outputStream = noCompression.openOutputStream(root, artifact.getFilePrefix())) {
				compositeArtifactRepositoryFactory.write(getArtifactRepositoryFacade().getRepository(), outputStream);
			}
			try (OutputStream outputStream = noCompression.openOutputStream(root, metadata.getFilePrefix())) {
				compositeMetadataRepositoryFactory.write(getMetadataRepositoryFacade().getRepository(), outputStream);
			}
			availableCompressions.put(P2Kind.artifact, Arrays.asList(noCompression));
			availableCompressions.put(P2Kind.metadata, Arrays.asList(noCompression));
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

		return new P2CompositeRepositoryImpl(path, p2index, availableCompressions, compositeArtifactRepositoryLoader,
				compositeMetadataRepositoryLoader,
				new CompositeRepositoryFacadeImpl(path, compositeArtifactRepositoryFactory.createEmpty()),
				new CompositeRepositoryFacadeImpl(path, compositeMetadataRepositoryFactory.createEmpty()));
	}

	public P2CompositeRepository loadComposite(Path path) throws IOException {
		return (P2CompositeRepository) loadInternal(path, P2Type.composite);
	}

	private final BiFunction<Path, DataCompression, CompositeRepositoryFacade> compositeArtifactRepositoryLoader = (
			path, dc) -> {
		try (InputStream is = dc.openInputStream(path, P2IndexType.compositeArtifacts.getFilePrefix())) {
			return new CompositeRepositoryFacadeImpl(
					path.resolve(P2IndexType.compositeArtifacts.getFilePrefix() + "." + dc.getFileSuffix()),
					compositeArtifactRepositoryFactory.read(is));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	};

	private final BiFunction<Path, DataCompression, CompositeRepositoryFacade> compositeMetadataRepositoryLoader = (
			path, dc) -> {
		try (InputStream is = dc.openInputStream(path, P2IndexType.compositeMetadata.getFilePrefix())) {
			return new CompositeRepositoryFacadeImpl(
					path.resolve(P2IndexType.compositeMetadata.getFilePrefix() + "." + dc.getFileSuffix()),
					compositeMetadataRepositoryFactory.read(is));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	};

	private P2CompositeRepository loadComposite(Path path, P2Index p2index,
			Map<P2Kind, List<DataCompression>> availableCompressions) throws IOException {

		return new P2CompositeRepositoryImpl(path, p2index, availableCompressions, compositeArtifactRepositoryLoader,
				compositeMetadataRepositoryLoader);
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

	enum P2Kind {
		artifact, metadata;

		public String getProperty() {
			return name() + ".repository.factory.order";
		}
	}

	public CommonP2Repository loadAny(Path path) throws IOException {
		return loadInternal(path, null);
	}

	private CommonP2Repository loadInternal(Path path, P2Type expectedType) throws IOException {
		P2Index p2index;
		try {
			p2index = new P2Index(path);
		} catch (NoSuchFileException e) {
			throw new NoRepositoryFoundException("No repository with p2.index found at " + path, e);
		}

		P2Type type = p2index.identifyType();
		if (expectedType != null && type != expectedType)
			throw new IllegalArgumentException(
					"Wrong repository type found at " + path + ". Expected " + expectedType + ", got " + type);

		Map<P2Kind, List<DataCompression>> availableCompressions = new HashMap<>();
		for (P2Kind kind : P2Kind.values()) {
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

	public class NoRepositoryFoundException extends RuntimeException {
		public NoRepositoryFoundException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
