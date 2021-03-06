package com.github.pms1.tppt.p2;

import java.util.Objects;

import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

public class ArtifactId {
	private final String id;
	private final Version version;
	private final String classifier;

	public ArtifactId(String id, Version version, String classifier) {
		Preconditions.checkNotNull(id);
		Preconditions.checkArgument(!id.isEmpty());
		Preconditions.checkNotNull(version);
		Preconditions.checkNotNull(classifier);
		Preconditions.checkArgument(!classifier.isEmpty());

		this.id = id;
		this.version = version;
		this.classifier = classifier;
	}

	@Override
	public String toString() {
		return id + "/" + version + "/" + classifier;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, version, classifier);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ArtifactId other = (ArtifactId) obj;
		return Objects.equals(id, other.id) && Objects.equals(version, other.version)
				&& Objects.equals(classifier, other.classifier);
	}

	public String getId() {
		return id;
	}

	public Version getVersion() {
		return version;
	}

	public String getClassifier() {
		return classifier;
	}
}
