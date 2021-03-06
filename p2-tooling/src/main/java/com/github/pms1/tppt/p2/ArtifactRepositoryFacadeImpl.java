package com.github.pms1.tppt.p2;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.pms1.ldap.AttributeDescription;
import com.github.pms1.ldap.SearchFilter;
import com.github.pms1.ldap.SearchFilterEvaluator;
import com.github.pms1.ldap.SearchFilterParser;
import com.github.pms1.tppt.p2.jaxb.artifact.Artifact;
import com.github.pms1.tppt.p2.jaxb.artifact.ArtifactProperty;
import com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository;
import com.github.pms1.tppt.p2.jaxb.artifact.Rule;
import com.google.common.base.Preconditions;

class ArtifactRepositoryFacadeImpl extends AbstractRepositoryFacade<ArtifactRepository>
		implements ArtifactRepositoryFacade {
	private final ArtifactRepository data;

	private Map<ArtifactId, ArtifactFacade> asMap;

	private final Path path;

	public ArtifactRepositoryFacadeImpl(Path path, ArtifactRepository foo) {
		super(ArtifactProperty::new);
		// Preconditions.checkNotNull(root);
		Preconditions.checkNotNull(foo);
		this.data = foo;
		this.path = path;
	}

	@Override
	public String toString() {
		return super.toString() + "(" + data + ")";
	}

	private static class ArtifactFacadeImpl implements ArtifactFacade {

		private final ArtifactId id;

		private final Artifact data;

		ArtifactFacadeImpl(Artifact a) {
			Preconditions.checkNotNull(a);

			this.data = a;
			this.id = new ArtifactId(a.getId(), a.getVersion(), a.getClassifier());
		}

		@Override
		public ArtifactId getId() {
			return id;
		}

		@Override
		public String getClassifier() {
			return data.getClassifier();
		}

		@Override
		public Artifact getData() {
			return data;
		}

		@Override
		public String getFormat() {
			return getPropertyString("format");
		}

		@Override
		public String getPropertyString(String key) {
			return data.getProperties().getProperty().stream().filter(p -> p.getName().equals(key))
					.map(ArtifactProperty::getValue).findAny().orElse(null);
		}

	}

	@Override
	public Map<ArtifactId, ArtifactFacade> getArtifacts() {
		if (asMap == null) {
			Map<ArtifactId, ArtifactFacade> map = new HashMap<>();
			for (com.github.pms1.tppt.p2.jaxb.artifact.Artifact a : data.getArtifacts().getArtifact()) {
				ArtifactFacadeImpl a2 = new ArtifactFacadeImpl(a);
				map.put(a2.getId(), a2);
			}
			asMap = Collections.unmodifiableMap(map);
		}
		return asMap;
	}

	private static SearchFilterParser parser = new SearchFilterParser().lenient();

	@Override
	public Path getArtifactUri(ArtifactId id) {
		ArtifactFacadeImpl artifact = (ArtifactFacadeImpl) getArtifacts().get(id);
		Preconditions.checkArgument(artifact != null, "No artifact '" + id + "'");

		String match = null;

		for (Rule r : data.getMappings().getRule()) {
			SearchFilter searchFilter = parser.parse(r.getFilter());

			if (new SearchFilterEvaluator().evaluate(searchFilter, p -> {
				String k = ((AttributeDescription) p).getKeystring();
				switch (k) {
				case "classifier":
					return artifact.data.getClassifier();
				case "format":
					return artifact.getFormat();
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

		if (!match.startsWith("${repoUrl}/"))
			throw new IllegalStateException();
		match = match.substring(11);

		Matcher m = Pattern.compile("[$][{]([^}]*)[}]").matcher(match);

		boolean result = m.find();
		if (result) {
			StringBuffer sb = new StringBuffer();
			do {
				String replacement;
				switch (m.group(1)) {
				// case "repoUrl":
				// replacement = root.toUri().toString();
				// break;
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
			match = sb.toString();
		}

		return path.resolveSibling(match);
	}

	@Override
	public Path getPath() {
		return path;
	}

	@Override
	public ArtifactRepository getRepository() {
		return data;
	}
}
