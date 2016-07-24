package com.github.pms1.tppt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.github.pms1.tppt.InterpolatedString.Visitor;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import de.pdark.decentxml.Attribute;
import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLIOSource;
import de.pdark.decentxml.XMLParser;

public class DeploymentHelper {
	private Attribute findTimestamp(Document d) {

		Attribute ts = null;

		for (Element e : d.getRootElement().getChild("properties").getChildren("property")) {
			Attribute attribute = e.getAttribute("name");
			if (!attribute.getValue().equals("p2.timestamp"))
				continue;

			if (ts != null)
				throw new IllegalStateException();
			ts = e.getAttribute("value");
			if (ts == null)
				throw new IllegalArgumentException();
		}

		if (ts == null)
			throw new IllegalArgumentException();

		return ts;
	}

	private LocalDateTime extractP2Timestamp(Path p) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(p, null)) {
			Path path = fs.getPath("artifacts.xml");

			XMLParser parser = new XMLParser();

			de.pdark.decentxml.Document d;

			try (InputStream is = Files.newInputStream(path)) {
				d = parser.parse(new XMLIOSource(is));
			}

			String ts = findTimestamp(d).getValue();

			long ts_ms = Long.parseLong(ts);

			return LocalDateTime.ofEpochSecond(ts_ms / 1000, (int) (ts_ms % 1000) * 1000000, ZoneOffset.UTC);
		}
	}

	private String getLayout(MavenProject p) {
		Plugin plugin = p.getPlugin("com.github.pms1.tppt:tppt-maven-plugin");

		Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
		Xpp3Dom layout = configuration.getChild("layout");
		if (layout == null)
			return "@{artifactId}-@{version}-@{timestamp}";
		else
			return layout.getValue();
	}

	Path getPath(MavenProject p) throws MojoExecutionException {
		Preconditions.checkNotNull(p);

		String layout = getLayout(p);
		InterpolatedString layout1 = InterpolatedString.parse(layout);
		Map<String, Object> context = new HashMap<>();
		context.put("group", p.getGroupId());
		context.put("artifactId", p.getArtifactId());
		context.put("version", p.getVersion());
		try {
			context.put("timestamp", extractP2Timestamp(p.getArtifact().getFile().toPath()));
		} catch (IOException e1) {
			throw new MojoExecutionException("foo", e1);
		}

		return Paths.get(interpolate(layout1, context));
	}

	RepositoryPathPattern getPathPattern(MavenProject p) {
		Preconditions.checkNotNull(p);

		String layout = getLayout(p);
		InterpolatedString layout1 = InterpolatedString.parse(layout);
		Map<String, Object> context = new HashMap<>();
		context.put("group", p.getGroupId());
		context.put("artifactId", p.getArtifactId());
		context.put("version", p.getVersion());
		context.put("timestamp", LocalDateTime.class);

		return interpolatePattern(layout1, context);
	}

	private String interpolate(InterpolatedString layout1, Map<String, Object> context) {
		StringBuilder b = new StringBuilder();

		layout1.accept(new Visitor() {

			@Override
			public void visitText(String text) {
				b.append(text);
			}

			@Override
			public void visitVariable(List<String> variable) {
				Object o = context.get(variable.get(0));
				String s;
				if (o == null) {
					throw new IllegalArgumentException("No variable: " + variable.get(0));
				} else if (o.getClass() == String.class) {
					if (variable.size() != 1)
						throw new IllegalArgumentException();
					s = o.toString();
				} else if (o.getClass() == LocalDateTime.class) {
					DateTimeFormatter formatter;
					if (variable.size() == 1)
						formatter = defaultFormatter;
					else if (variable.size() == 2)
						formatter = DateTimeFormatter.ofPattern(variable.get(1));
					else
						throw new IllegalArgumentException();
					s = formatter.format((LocalDateTime) o);
				} else {
					throw new IllegalArgumentException();
				}
				b.append(s);
			}

		});

		return b.toString();
	}

	static private final Joiner pathJoiner = Joiner.on("/");

	// should be: <code>DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")</code>
	// but see https://bugs.openjdk.java.net/browse/JDK-8031085
	static private final DateTimeFormatter defaultFormatter = new DateTimeFormatterBuilder()
			.appendPattern("yyyyMMddHHmmss").appendValue(ChronoField.MILLI_OF_SECOND, 3).toFormatter();

	private RepositoryPathPattern interpolatePattern(InterpolatedString layout1, Map<String, Object> context) {
		StringBuilder b = new StringBuilder();

		Map<String, Function<String, Object>> postProcessors = new HashMap<>();

		layout1.accept(new Visitor() {

			@Override
			public void visitText(String text) {
				b.append(Pattern.quote(text));
			}

			@Override
			public void visitVariable(List<String> variable) {
				Object o = context.get(variable.get(0));

				if (o == null) {
					throw new IllegalArgumentException("No variable: " + variable.get(0));
				} else if (o.getClass() == String.class) {
					b.append(Pattern.quote((String) o));
				} else if (o == LocalDateTime.class) {
					DateTimeFormatter formatter;
					if (variable.size() == 1)
						formatter = defaultFormatter;
					else if (variable.size() == 2)
						formatter = DateTimeFormatter.ofPattern(variable.get(1));
					else
						throw new IllegalArgumentException();
					postProcessors.put(variable.get(0), p -> p == null ? null : LocalDateTime.parse(p, formatter));
					b.append("(?<" + variable.get(0) + ">.*)");
				} else {
					throw new IllegalArgumentException();
				}
			}

		});

		Pattern pattern = Pattern.compile(b.toString());

		return new RepositoryPathPattern() {

			@Override
			public RepositoryPathMatcher matcher(Path path) {

				return new RepositoryPathMatcher() {

					java.util.regex.Matcher m;

					@Override
					public boolean matches() {
						m = pattern.matcher(pathJoiner.join(path));
						return m.matches();
					}

					@Override
					public <T> T get(String variable, Class<T> type) {
						if (m == null)
							throw new IllegalStateException();
						return type.cast(postProcessors.get(variable).apply(m.group(variable)));
					}

				};
			}
		};

	}
}
