package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = P2RepositoryFactory.class)
public class P2RepositoryFactory {

	@Requirement
	private ArtifactRepositoryFactory artifactRepositoryFactory;

	@Requirement
	private MetadataRepositoryFactory metadataRepositoryFactory;

	@Requirement
	private Map<String, DataCompression> compressions;

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

		if (availableCompressions.isEmpty()) {
			throw new IllegalArgumentException("No data found at '" + p + "', tried " + tried);
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

	public static String P2INDEX = "p2.index";
	public static String ARTIFACT_PREFIX = "artifacts";
	public static String METADATA_PREFIX = "content";

	public P2Repository create(Path p) throws IOException {
		Properties p2index = new Properties();

		try (InputStream is = Files.newInputStream(p.resolve(P2INDEX))) {
			p2index.load(is);
		} catch (java.nio.file.NoSuchFileException e) {
			return null;
		}

		if (!Objects.equals(p2index.getProperty("version", null), "1"))
			throw new Error();

		Set<DataCompression> availableMetadata = findData(p, METADATA_PREFIX);
		Set<DataCompression> availableArtifacts = findData(p, ARTIFACT_PREFIX);

		DataCompression preferedMetadata = prefered(p2index.getProperty("metadata.repository.factory.order"),
				METADATA_PREFIX, compressions.values(), availableMetadata);
		DataCompression preferedArtifacts = prefered(p2index.getProperty("artifact.repository.factory.order"),
				ARTIFACT_PREFIX, compressions.values(), availableArtifacts);

		return new P2RepositoryImpl(p, new CachingSupplier<ArtifactRepositoryFacade>(() -> {
			try (InputStream is = preferedArtifacts.openStream(p, ARTIFACT_PREFIX)) {
				return new ArtifactRepositoryFacadeImpl(
						p.resolve(ARTIFACT_PREFIX + "." + preferedArtifacts.getFileSuffix()),
						artifactRepositoryFactory.read(is));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}), availableArtifacts, preferedArtifacts, new CachingSupplier<MetadataRepositoryFacade>(() -> {
			try (InputStream is = preferedMetadata.openStream(p, METADATA_PREFIX)) {
				return new MetadataRepositoryFacadeImpl(
						p.resolve(METADATA_PREFIX + "." + preferedMetadata.getFileSuffix()),
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
}
