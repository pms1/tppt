package com.github.pms1.tppt.p2;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;

class ArtifactRepositoryData {
	public ArtifactRepositoryData.Artifacts artifacts;
	public ArtifactRepositoryData.Properties properties;
	public ArtifactRepositoryData.Mappings mappings;

	public static class Properties {
		public List<ArtifactRepositoryData.Property> property;

		@Override
		public String toString() {
			return com.google.common.base.Objects.toStringHelper(this).add("property", property).toString();
		}
	}

	public static class Mappings {
		public List<Rule> rule;

		@Override
		public String toString() {
			return com.google.common.base.Objects.toStringHelper(this).add("rule", rule).toString();
		}
	}

	public static class Rule {
		@XmlAttribute
		public String filter;

		@XmlAttribute
		public String output;

		@Override
		public String toString() {
			return com.google.common.base.Objects.toStringHelper(this).add("filter", filter).add("output", output)
					.toString();
		}
	}

	public static class Property {

		@XmlAttribute
		public String name;

		@XmlAttribute
		public String value;

		@Override
		public String toString() {
			return com.google.common.base.Objects.toStringHelper(this).add("name", name).add("value", value).toString();
		}
	}

	public static class Artifact {
		@XmlAttribute
		public String classifier;

		@XmlAttribute
		public String id;

		@XmlAttribute
		public String version;

		@Override
		public String toString() {
			return com.google.common.base.Objects.toStringHelper(this).add("id", id).add("version", version)
					.add("classifier", classifier).toString();
		}
	}

	public static class Artifacts {
		public List<ArtifactRepositoryData.Artifact> artifact;

		@Override
		public String toString() {
			return com.google.common.base.Objects.toStringHelper(this).add("artifact", artifact).toString();
		}
	}

	@Override
	public String toString() {
		return com.google.common.base.Objects.toStringHelper(this).add("artifacts", artifacts).add("mappings", mappings)
				.add("properties", properties).toString();
	}
}