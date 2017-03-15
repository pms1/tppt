package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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

	private Set<DataCompression> findData(Path p, String prefix) {
		Set<DataCompression> availableCompressions = new HashSet<>();

		List<String> tried = new ArrayList<>(5);

		for (DataCompression c : compressions.values()) {
			Path p1 = p.resolve(prefix + "." + c.getFileSuffix());
			tried.add(p1.getFileName().toString());
			if (Files.exists(p1))
				availableCompressions.add(c);
		}

		return availableCompressions;
	}

	private DataCompression prefered(String p2entry, String prefix, Collection<DataCompression> compressions,
			Set<DataCompression> available) {

		DataCompression cc = null;

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
			if (cc != null)
				break;
		}

		throw new Error();
	}

	public static final String P2INDEX = "p2.index";
	public static final String ARTIFACT_PREFIX = "artifacts";
	public static final String METADATA_PREFIX = "content";
	public static final String COMPOSITE_ARTIFACT_PREFIX = "compositeArtifacts";
	public static final String COMPOSITE_METADATA_PREFIX = "compositeContent";

	private static final String P2_VERSION = "1";
	private static final String P2_VERSION_PROPERTY = "version";
	private static final String P2_METADATA_PROPERTY = "metadata.repository.factory.order";
	private static final String P2_ARTIFACT_PROPERTY = "artifact.repository.factory.order";

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

		P2IndexEntry getIndexEntry(P2Kind artifact) {
			String p2entry = properties.getProperty(artifact.getProperty());
			if (p2entry == null)
				throw new IllegalArgumentException("No entry in p2.index for '" + artifact + "'");

			P2IndexEntry result = null;

			for (String e : p2entry.trim().split(",")) {
				if (e.equals("!"))
					break;

				int idx = e.lastIndexOf('.');
				if (idx == -1)
					throw new IllegalArgumentException();

				P2IndexEntry t = P2IndexEntry.valueOf(e.substring(0, idx));
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
				P2IndexEntry entry = getIndexEntry(t);
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

	private P2Repository loadContent(Path path, P2Index p2index) throws IOException {

		Set<DataCompression> availableMetadata = findData(path, METADATA_PREFIX);
		Set<DataCompression> availableArtifacts = findData(path, ARTIFACT_PREFIX);

		DataCompression preferedMetadata = prefered(p2index.getProperty(P2_METADATA_PROPERTY), METADATA_PREFIX,
				compressions.values(), availableMetadata);
		DataCompression preferedArtifacts = prefered(p2index.getProperty(P2_ARTIFACT_PROPERTY), ARTIFACT_PREFIX,
				compressions.values(), availableArtifacts);

		return new P2RepositoryImpl(path, new CachingSupplier<ArtifactRepositoryFacade>(() -> {
			try (InputStream is = preferedArtifacts.openInputStream(path, ARTIFACT_PREFIX)) {
				return new ArtifactRepositoryFacadeImpl(
						path.resolve(ARTIFACT_PREFIX + "." + preferedArtifacts.getFileSuffix()),
						artifactRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}), availableArtifacts, preferedArtifacts, new CachingSupplier<MetadataRepositoryFacade>(() -> {
			try (InputStream is = preferedMetadata.openInputStream(path, METADATA_PREFIX)) {
				return new MetadataRepositoryFacadeImpl(
						path.resolve(METADATA_PREFIX + "." + preferedMetadata.getFileSuffix()),
						metadataRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}), availableMetadata, preferedMetadata);
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
		protected final String artifactPrefix;
		protected final String metadataPrefix;
		protected final Path root;
		protected final P2Index p2index;

		protected Set<DataCompression> availableMetadata;
		protected Set<DataCompression> availableArtifacts;

		protected AbstractRepository(P2Type type, String artifactPrefix, String metadataPrefix, Path root,
				P2Index p2index) {
			Objects.requireNonNull(artifactPrefix);
			this.artifactPrefix = artifactPrefix;
			Objects.requireNonNull(metadataPrefix);
			this.metadataPrefix = metadataPrefix;
			Objects.requireNonNull(root);
			this.root = root;
			Objects.requireNonNull(p2index);
			this.p2index = p2index;

			availableMetadata = findData(root, type.getIndexEntry(P2Kind.metadata).name());
			availableArtifacts = findData(root, type.getIndexEntry(P2Kind.artifact).name());
		}

		@Override
		public Set<DataCompression> getMetadataDataCompressions() {
			return availableMetadata;
		}

		@Override
		public Set<DataCompression> getArtifactDataCompressions() {
			return availableArtifacts;
		}

		@Override
		public void setCompression(DataCompression... compressions) throws IOException {
			Preconditions.checkState(preferredArtifactCompression != null);
			Preconditions.checkState(preferredMetadataCompression != null);

			boolean deleteOldArtifact = true;
			boolean deleteOldMetadata = true;

			for (DataCompression c : compressions) {
				if (c == preferredArtifactCompression)
					deleteOldArtifact = false;
				else
					try (InputStream is = preferredArtifactCompression.openInputStream(root, artifactPrefix)) {
						try (OutputStream os = c.openOutputStream(root, artifactPrefix)) {
							IOUtil.copy(is, os);
						}
					}
				if (c == preferredMetadataCompression)
					deleteOldMetadata = false;
				else
					try (InputStream is = preferredMetadataCompression.openInputStream(root, metadataPrefix)) {
						try (OutputStream os = c.openOutputStream(root, metadataPrefix)) {
							IOUtil.copy(is, os);
						}
					}
			}

			if (deleteOldArtifact)
				Files.delete(root.resolve(artifactPrefix + "." + preferredArtifactCompression.getFileSuffix()));

			if (deleteOldMetadata)
				Files.delete(root.resolve(metadataPrefix + "." + preferredMetadataCompression.getFileSuffix()));

			p2index.set(P2_ARTIFACT_PROPERTY, COMPOSITE_ARTIFACT_PREFIX, compressions);
			p2index.set(P2_METADATA_PROPERTY, COMPOSITE_METADATA_PREFIX, compressions);
			p2index.write(root);

			preferredArtifactCompression = compressions[0];
			preferredMetadataCompression = compressions[0];
		}
	}

	class P2CompositeRepositoryImpl extends AbstractRepository implements P2CompositeRepository {
		final CachingSupplier<CompositeRepositoryFacade> artifact;
		final CachingSupplier<CompositeRepositoryFacade> metadata;
		final Path root;
		final P2Index p2index;

		public P2CompositeRepositoryImpl(Path root, P2Index p2index,
				CachingSupplier<CompositeRepositoryFacade> artifact,
				CachingSupplier<CompositeRepositoryFacade> metadata) {
			super(P2Type.composite, COMPOSITE_ARTIFACT_PREFIX, COMPOSITE_METADATA_PREFIX, root, p2index);
			Objects.requireNonNull(root);
			this.root = root;
			Objects.requireNonNull(p2index);
			this.p2index = p2index;
			Objects.requireNonNull(artifact);
			this.artifact = artifact;
			Objects.requireNonNull(metadata);
			this.metadata = metadata;
		}

		@Override
		public CompositeRepositoryFacade getArtifactRepositoryFacade() throws IOException {
			return artifact.get();
		}

		@Override
		public CompositeRepositoryFacade getMetadataRepositoryFacade() throws IOException {
			return metadata.get();
		}

		@Override
		public void save() throws IOException {
			p2index.set(P2_ARTIFACT_PROPERTY, COMPOSITE_ARTIFACT_PREFIX, noCompression);
			p2index.set(P2_METADATA_PROPERTY, COMPOSITE_METADATA_PREFIX, noCompression);
			p2index.write(root);
			try (OutputStream outputStream = noCompression.openOutputStream(root, COMPOSITE_ARTIFACT_PREFIX)) {
				compositeArtifactRepositoryFactory.write(artifact.get().getRepository(), outputStream);
			}
			try (OutputStream outputStream = noCompression.openOutputStream(root, COMPOSITE_METADATA_PREFIX)) {
				compositeMetadataRepositoryFactory.write(metadata.get().getRepository(), outputStream);
			}
			preferredArtifactCompression = noCompression;
			preferredMetadataCompression = noCompression;
		}

		@Override
		public <T> T accept(P2RepositoryVisitor<T> visitor) {
			return visitor.visit(this);
		}

		@Override
		public Path getPath() {
			return root;
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
		Set<DataCompression> artifactAvailable = findData(path, COMPOSITE_ARTIFACT_PREFIX);
		if (artifactAvailable.isEmpty())
			throw new IllegalArgumentException();
		DataCompression artifactCompressions = p2index.prefered(P2_ARTIFACT_PROPERTY, COMPOSITE_ARTIFACT_PREFIX,
				compressions.values(), artifactAvailable);

		Set<DataCompression> metadataAvailable = findData(path, COMPOSITE_METADATA_PREFIX);
		if (metadataAvailable.isEmpty())
			throw new IllegalArgumentException();
		DataCompression metadataCompressions = p2index.prefered(P2_METADATA_PROPERTY, COMPOSITE_METADATA_PREFIX,
				compressions.values(), metadataAvailable);

		CachingSupplier<CompositeRepositoryFacade> artifactRepository = onDemand(() -> {
			try (InputStream is = artifactCompressions.openInputStream(path, COMPOSITE_ARTIFACT_PREFIX)) {
				return new CompositeRepositoryFacadeImpl(
						path.resolve(ARTIFACT_PREFIX + "." + artifactCompressions.getFileSuffix()),
						compositeArtifactRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		CachingSupplier<CompositeRepositoryFacade> metadataRepository = onDemand(() -> {
			try (InputStream is = metadataCompressions.openInputStream(path, COMPOSITE_METADATA_PREFIX)) {
				return new CompositeRepositoryFacadeImpl(
						path.resolve(METADATA_PREFIX + "." + metadataCompressions.getFileSuffix()),
						compositeMetadataRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		return new P2CompositeRepositoryImpl(path, p2index, artifactRepository, metadataRepository);
	}

	private enum P2IndexEntry {
		compositeArtifacts(P2Kind.artifact, P2Type.composite), //
		compositeContent(P2Kind.metadata, P2Type.composite), //
		artifacts(P2Kind.artifact, P2Type.content), //
		content(P2Kind.metadata, P2Type.content);

		private final P2Kind kind;
		private final P2Type type;

		P2IndexEntry(P2Kind key, P2Type type) {
			Objects.requireNonNull(key);
			Objects.requireNonNull(type);
			this.kind = key;
			this.type = type;
		}

		P2Kind getKind() {
			return kind;
		}

		P2Type getType() {
			return type;
		}
	}

	private enum P2Type {
		composite, //
		content;

		public P2IndexEntry getIndexEntry(P2Kind d) {
			for (P2IndexEntry e : P2IndexEntry.values())
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
			throw new NoRepositoryFoundException("no repository with p2.index found at " + path, e);
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
