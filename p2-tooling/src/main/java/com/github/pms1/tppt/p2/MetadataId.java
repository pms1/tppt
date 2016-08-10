package com.github.pms1.tppt.p2;

import java.util.Objects;

import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

public class MetadataId {
	private final String id;
	private final Version version;

	public MetadataId(String id, Version version) {
		Preconditions.checkNotNull(id);
		Preconditions.checkArgument(!id.isEmpty());
		Preconditions.checkNotNull(version);

		this.id = id;
		this.version = version;
	}

	@Override
	public String toString() {
		return id + "/" + version;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, version);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MetadataId other = (MetadataId) obj;
		return Objects.equals(id, other.id) && Objects.equals(version, other.version);
	}

	public String getId() {
		return id;
	}

	public Version getVersion() {
		return version;
	}
}
