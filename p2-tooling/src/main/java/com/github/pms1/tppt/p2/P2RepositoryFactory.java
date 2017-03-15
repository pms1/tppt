package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
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

	private Set<DataCompression> findData(Path p, P2IndexType prefix) {
		Set<DataCompression> availableCompressions = new HashSet<>();

		for (DataCompression c : compressions.values()) {
			Path p1 = p.resolve(prefix.getFilePrefix() + "." + c.getFileSuffix());
			if (Files.exists(p1))
				availableCompressions.add(c);
		}

		return availableCompressions;
	}

	static private DataCompression prefered(String p2entry, P2IndexType prefix,
			Collection<DataCompression> compressions, Set<DataCompression> available) {

		DataCompression cc = null;

		for (String e : p2entry.trim().split(",")) {
			if (e.equals("!"))
				break;
			if (!e.startsWith(prefix.getFilePrefix() + "."))
				throw new IllegalArgumentException("Unhandled p2.index entry '" + e + "'");
			e = e.substring(prefix.getFilePrefix().length() + 1);
			for (DataCompression c : compressions) {
				if (c.getP2IndexSuffix().equals(e) && available.contains(c)) {
					return c;
				}
			}
			if (cc != null)
				break;
		}

		throw new Error("entry=>" + p2entry + "< >" + prefix + "< c=" + compressions + " " + available);
	}

	public static final String P2INDEX = "p2.index";
	@Deprecated
	public static final String ARTIFACT_PREFIX = P2IndexType.artifacts.getFilePrefix();
	@Deprecated
	public static final String METADATA_PREFIX = P2IndexType.metadata.getFilePrefix();

	private static final String P2_VERSION = "1";
	private static final String P2_VERSION_PROPERTY = "version";

	static class P2Index {
		final private Properties properties;
		private boolean dirty;

		P2Index() {
			properties = new Properties();
			properties.put(P2_VERSION_PROPERTY, P2_VERSION);
			dirty = true;
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

			dirty = false;
		}

		public void set(String property, String prefix, DataCompression... compressions) {
			LinkedHashSet<String> entries = new LinkedHashSet<>();

			for (DataCompression c : compressions)
				entries.add(prefix + "." + c.getP2IndexSuffix());
			entries.add("!");

			String newEntry = entries.stream().collect(Collectors.joining(","));

			dirty |= !Objects.equals(properties.getProperty(property), newEntry);
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

		public String getProperty(String p2MetadataProperty) {
			return properties.getProperty(p2MetadataProperty);
		}

	}

	public P2Repository loadContent(Path path) throws IOException {
		P2Index p2index;
		try {
			p2index = new P2Index(path);
		} catch (NoSuchFileException e) {
			throw new NoRepositoryFoundException("no repository with p2.index found at " + path, e);
		}

		return loadContent(path, p2index);
	}

	class P2RepositoryImpl extends AbstractRepository implements P2Repository {

		private final Supplier<ArtifactRepositoryFacade> artifactRepositoryFacadeSupplier;
		private final Supplier<MetadataRepositoryFacade> metadataRepositoryFacadeSupplier;

		public P2RepositoryImpl(Path path, P2Index p2index,
				Supplier<ArtifactRepositoryFacade> artifactRepositorySupplier,
				Supplier<MetadataRepositoryFacade> metadataRepositoryProducer) {
			super(P2Type.content, path, p2index);
			this.artifactRepositoryFacadeSupplier = artifactRepositorySupplier;
			this.metadataRepositoryFacadeSupplier = metadataRepositoryProducer;
		}

		@Override
		public ArtifactRepositoryFacade getArtifactRepositoryFacade() throws IOException {
			return artifactRepositoryFacadeSupplier.get();
		}

		@Override
		public MetadataRepositoryFacade getMetadataRepositoryFacade() throws IOException {
			return metadataRepositoryFacadeSupplier.get();
		}

		@Override
		public <T> T accept(P2RepositoryVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	private P2Repository loadContent(Path path, P2Index p2index) throws IOException {
		Set<DataCompression> availableArtifacts = findData(path, P2IndexType.artifacts);
		if (availableArtifacts.isEmpty())
			throw new IllegalArgumentException("Not found: " + path + " " + P2IndexType.artifacts.getFilePrefix());

		Set<DataCompression> availableMetadata = findData(path, P2IndexType.metadata);
		if (availableMetadata.isEmpty())
			throw new IllegalArgumentException("Not found: " + path + " " + P2IndexType.metadata.getFilePrefix());

		DataCompression preferedArtifacts = prefered(p2index.getProperty(P2Kind.artifact.getProperty()),
				P2IndexType.artifacts, compressions.values(), availableArtifacts);
		DataCompression preferedMetadata = prefered(p2index.getProperty(P2Kind.metadata.getProperty()),
				P2IndexType.metadata, compressions.values(), availableMetadata);

		return new P2RepositoryImpl(path, p2index, new CachingSupplier<ArtifactRepositoryFacade>(() -> {
			try (InputStream is = preferedArtifacts.openInputStream(path, P2IndexType.artifacts.getFilePrefix())) {
				return new ArtifactRepositoryFacadeImpl(
						path.resolve(P2IndexType.artifacts.getFilePrefix() + "." + preferedArtifacts.getFileSuffix()),
						artifactRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}), new CachingSupplier<MetadataRepositoryFacade>(() -> {
			try (InputStream is = preferedMetadata.openInputStream(path, P2IndexType.metadata.getFilePrefix())) {
				return new MetadataRepositoryFacadeImpl(
						path.resolve(P2IndexType.metadata.getFilePrefix() + "." + preferedMetadata.getFileSuffix()),
						metadataRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}));
	}

	private static class CachingSupplier<T> implements Supplier<T> {
		private Supplier<T> next;
		private T cache;

		CachingSupplier(Supplier<T> next) {
			this.next = next;
		}

		@Override
		public T get() {
			if (cache == null) {
				cache = next.get();
				if (cache == null)
					throw new IllegalStateException();
			}
			return cache;
		}
	}

	abstract class AbstractRepository implements CommonP2Repository {
		protected DataCompression preferredArtifactCompression;
		protected DataCompression preferredMetadataCompression;
		protected final Path root;
		protected final P2Index p2index;
		protected final P2IndexType artifact;
		protected final P2IndexType metadata;
		protected Set<DataCompression> availableMetadata;
		protected Set<DataCompression> availableArtifacts;

		protected AbstractRepository(P2Type type, Path root, P2Index p2index) {
			Objects.requireNonNull(type);
			this.artifact = type.getIndexEntry(P2Kind.artifact);
			this.metadata = type.getIndexEntry(P2Kind.metadata);
			Objects.requireNonNull(root);
			this.root = root;
			Objects.requireNonNull(p2index);
			this.p2index = p2index;

			availableArtifacts = findData(root, type.getIndexEntry(P2Kind.artifact));
			availableMetadata = findData(root, type.getIndexEntry(P2Kind.metadata));
		}

		@Override
		final public Set<DataCompression> getMetadataDataCompressions() {
			return availableMetadata;
		}

		@Override
		final public Set<DataCompression> getArtifactDataCompressions() {
			return availableArtifacts;
		}

		@Override
		public void setCompression(DataCompression... compressions) throws IOException {
			Preconditions.checkNotNull(compressions);
			Preconditions.checkArgument(compressions.length > 0);
			Preconditions.checkState(preferredArtifactCompression != null);
			Preconditions.checkState(preferredMetadataCompression != null);

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

			preferredArtifactCompression = compressions[0];
			preferredMetadataCompression = compressions[0];
		}

		@Override
		public Path getPath() {
			return root;
		}
	}

	class P2CompositeRepositoryImpl extends AbstractRepository implements P2CompositeRepository {
		final CachingSupplier<CompositeRepositoryFacade> artifactSupplier;
		final CachingSupplier<CompositeRepositoryFacade> metadataSupplier;

		public P2CompositeRepositoryImpl(Path root, P2Index p2index,
				CachingSupplier<CompositeRepositoryFacade> artifactSupplier,
				CachingSupplier<CompositeRepositoryFacade> metadataSupplier) {
			super(P2Type.composite, root, p2index);
			Objects.requireNonNull(artifactSupplier);
			this.artifactSupplier = artifactSupplier;
			Objects.requireNonNull(metadataSupplier);
			this.metadataSupplier = metadataSupplier;
		}

		@Override
		public CompositeRepositoryFacade getArtifactRepositoryFacade() throws IOException {
			return artifactSupplier.get();
		}

		@Override
		public CompositeRepositoryFacade getMetadataRepositoryFacade() throws IOException {
			return metadataSupplier.get();
		}

		@Override
		public void save() throws IOException {
			p2index.set(P2RepositoryFactory.P2Kind.artifact.getProperty(), artifact.getFilePrefix(), noCompression);
			p2index.set(P2RepositoryFactory.P2Kind.metadata.getProperty(), metadata.getFilePrefix(), noCompression);
			p2index.write(root);
			try (OutputStream outputStream = noCompression.openOutputStream(root, artifact.getFilePrefix())) {
				compositeArtifactRepositoryFactory.write(artifactSupplier.get().getRepository(), outputStream);
			}
			try (OutputStream outputStream = noCompression.openOutputStream(root, metadata.getFilePrefix())) {
				compositeMetadataRepositoryFactory.write(metadataSupplier.get().getRepository(), outputStream);
			}
			preferredArtifactCompression = noCompression;
			preferredMetadataCompression = noCompression;
		}

		@Override
		public <T> T accept(P2RepositoryVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	private <T> CachingSupplier<T> onDemand(Supplier<T> t) {
		return new CachingSupplier<T>(t);
	}

	public P2CompositeRepository createComposite(Path path) throws IOException {
		P2Index p2index = new P2Index();

		return new P2CompositeRepositoryImpl(path, p2index, onDemand(
				() -> new CompositeRepositoryFacadeImpl(path, compositeArtifactRepositoryFactory.createEmpty())),
				onDemand(() -> new CompositeRepositoryFacadeImpl(path,
						compositeMetadataRepositoryFactory.createEmpty())));
	}

	public P2CompositeRepository loadComposite(Path path) throws IOException {
		P2Index p2index;
		try {
			p2index = new P2Index(path);
		} catch (NoSuchFileException e) {
			throw new NoRepositoryFoundException("no repository with p2.index found at " + path, e);
		}
		return loadComposite(path, p2index);
	}

	private P2CompositeRepository loadComposite(Path path, P2Index p2index) throws IOException {
		Set<DataCompression> artifactAvailable = findData(path, P2IndexType.compositeArtifacts);
		if (artifactAvailable.isEmpty())
			throw new IllegalArgumentException(
					"Not found: " + path + " " + P2IndexType.compositeArtifacts.getFilePrefix());
		DataCompression artifactCompressions = p2index.prefered(P2Kind.artifact.getProperty(),
				P2IndexType.compositeArtifacts.getFilePrefix(), compressions.values(), artifactAvailable);

		Set<DataCompression> metadataAvailable = findData(path, P2IndexType.compositeMetadata);
		if (metadataAvailable.isEmpty())
			throw new IllegalArgumentException(
					"Not found: " + path + " " + P2IndexType.compositeMetadata.getFilePrefix());
		DataCompression metadataCompressions = p2index.prefered(P2Kind.metadata.getProperty(),
				P2IndexType.compositeMetadata.getFilePrefix(), compressions.values(), metadataAvailable);

		CachingSupplier<CompositeRepositoryFacade> artifactRepository = onDemand(() -> {
			try (InputStream is = artifactCompressions.openInputStream(path,
					P2IndexType.compositeArtifacts.getFilePrefix())) {
				return new CompositeRepositoryFacadeImpl(path.resolve(
						P2IndexType.compositeArtifacts.getFilePrefix() + "." + artifactCompressions.getFileSuffix()),
						compositeArtifactRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		CachingSupplier<CompositeRepositoryFacade> metadataRepository = onDemand(() -> {
			try (InputStream is = metadataCompressions.openInputStream(path,
					P2IndexType.compositeMetadata.getFilePrefix())) {
				return new CompositeRepositoryFacadeImpl(path.resolve(
						P2IndexType.compositeMetadata.getFilePrefix() + "." + metadataCompressions.getFileSuffix()),
						compositeMetadataRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		return new P2CompositeRepositoryImpl(path, p2index, artifactRepository, metadataRepository);
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

	private enum P2Kind {
		artifact, metadata;

		public String getProperty() {
			return name() + ".repository.factory.order";
		}
	}

	public CommonP2Repository loadAny(Path path) throws IOException {
		P2Index p2index;
		try {
			p2index = new P2Index(path);
		} catch (NoSuchFileException e) {
			throw new NoRepositoryFoundException("No repository with p2.index found at " + path, e);
		}

		P2Type type = p2index.identifyType();
		switch (type) {
		case composite:
			return loadComposite(path, p2index);
		case content:
			return loadContent(path, p2index);
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
