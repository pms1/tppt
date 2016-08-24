package com.github.pms1.tppt.p2;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.pms1.ldap.AttributeDescription;
import com.github.pms1.ldap.SearchFilter;
import com.github.pms1.ldap.SearchFilterEvaluator;
import com.github.pms1.ldap.SearchFilterParser;
import com.github.pms1.tppt.p2.jaxb.artifact.Rule;
import com.google.common.base.Preconditions;

public class ArtifactRepositoryFactoryImpl implements ArtifactRepositoryFacade {
	private final com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository data;

	private Map<ArtifactId, Artifact> asMap;

	private final URI root;

	public ArtifactRepositoryFactoryImpl(URI root, com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository foo) {
		// Preconditions.checkNotNull(root);
		Preconditions.checkNotNull(foo);
		this.data = foo;
		this.root = root;
	}

	@Override
	public String toString() {
		return super.toString() + "(" + data + ")";
	}

	private static class ArtifactImpl implements Artifact {

		private final ArtifactId id;

		private final com.github.pms1.tppt.p2.jaxb.artifact.Artifact data;

		ArtifactImpl(com.github.pms1.tppt.p2.jaxb.artifact.Artifact a) {
			Preconditions.checkNotNull(a);

			this.data = a;
			this.id = new ArtifactId(a.getId(), a.getVersion());
		}

		@Override
		public ArtifactId getId() {
			return id;
		}

		@Override
		public String getClassifier() {
			return data.getClassifier();
		}
	}

	@Override
	public Map<ArtifactId, Artifact> getArtifacts() {
		if (asMap == null) {
			Map<ArtifactId, Artifact> map = new HashMap<>();
			for (com.github.pms1.tppt.p2.jaxb.artifact.Artifact a : data.getArtifacts().getArtifact()) {
				ArtifactImpl a2 = new ArtifactImpl(a);
				map.put(a2.getId(), a2);
			}
			asMap = Collections.unmodifiableMap(map);
		}
		return asMap;
	}

	private static SearchFilterParser parser = new SearchFilterParser().lenient();

	@Override
	public URI getArtifactUri(ArtifactId id) {
		ArtifactImpl artifact = (ArtifactImpl) getArtifacts().get(id);
		Preconditions.checkArgument(artifact != null, "No artifact '" + id + "'");

		String match = null;

		for (Rule r : data.getMappings().getRule()) {
			SearchFilter searchFilter = parser.parse(r.getFilter());

			if (new SearchFilterEvaluator().evaluate(searchFilter, p -> {
				String k = ((AttributeDescription) p).getKeystring();
				switch (k) {
				case "classifier":
					return artifact.data.getClassifier();
				default:
					throw new Error("" + k);
				}
			})) {
				if (match == null)
					match = r.getOutput();
				else
					throw new IllegalStateException();
			}

		}

		if (match == null)
			throw new IllegalStateException();

		Matcher m = Pattern.compile("[$][{]([^}]*)[}]").matcher(match);

		boolean result = m.find();
		if (result) {
			StringBuffer sb = new StringBuffer();
			do {
				String replacement;
				switch (m.group(1)) {
				case "repoUrl":
					replacement = root.toString();
					break;
				case "id":
					replacement = artifact.data.getId();
					break;
				case "version":
					replacement = artifact.data.getVersion().toString();
					break;
				default:
					throw new IllegalStateException("Unhandled substitution '" + m.group(1) + "'");
				}
				m.appendReplacement(sb, replacement);
				result = m.find();
			} while (result);
			m.appendTail(sb);
			return URI.create(sb.toString());
		}
		return URI.create(match);
	}
}
