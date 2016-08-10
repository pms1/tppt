package com.github.pms1.tppt.p2;

import java.net.URI;
import java.util.Map;

import com.google.common.base.Preconditions;

public class MetadataRepositoryImpl implements MetadataRepository {
	private final com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository data;

	private Map<MetadataId, Metadata> asMap;

	private final URI root;

	public MetadataRepositoryImpl(URI root, com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository foo) {
		Preconditions.checkNotNull(root);
		Preconditions.checkNotNull(foo);
		this.data = foo;
		this.root = root;
	}

	@Override
	public String toString() {
		return super.toString() + "(" + data + ")";
	}

	// private static class MetadataImpl implements Metadata {
	//
	// private final MetadataId id;
	//
	// private final MetadataRepositoryData.Metadata data;
	//
	// MetadataImpl(MetadataRepositoryData.Metadata data) {
	// Preconditions.checkNotNull(data);
	//
	// this.data = data;
	// this.id = new MetadataId(data.id, VersionParser.valueOf(data.version));
	// }
	//
	// @Override
	// public MetadataId getId() {
	// return id;
	// }
	//
	// @Override
	// public String getClassifier() {
	// return data.classifier;
	// }
	// }

	// @Override
	// public Map<MetadataId, Metadata> getMetadatas() {
	// if (asMap == null) {
	// Map<MetadataId, Metadata> map = new HashMap<>();
	// for (MetadataRepositoryData.Metadata a : data.Metadatas.Metadata) {
	// MetadataImpl a2 = new MetadataImpl(a);
	// map.put(a2.getId(), a2);
	// }
	// asMap = Collections.unmodifiableMap(map);
	// }
	// return asMap;
	// }
}
