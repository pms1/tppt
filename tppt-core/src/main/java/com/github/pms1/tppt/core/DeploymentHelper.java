package com.github.pms1.tppt.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Startable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.StartingException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.StoppingException;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.github.pms1.tppt.core.InterpolatedString.Visitor;
import com.github.pms1.tppt.p2.ArtifactId;
import com.github.pms1.tppt.p2.ArtifactRepositoryFacade;
import com.github.pms1.tppt.p2.CommonP2Repository;
import com.github.pms1.tppt.p2.DataCompression;
import com.github.pms1.tppt.p2.P2CompositeRepository;
import com.github.pms1.tppt.p2.P2Repository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.P2RepositoryFactory.P2IndexType;
import com.github.pms1.tppt.p2.P2RepositoryVisitor;
import com.github.pms1.tppt.p2.PathByteSource;
import com.github.pms1.tppt.p2.RepositoryFacade;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;

@Singleton
@Named
public class DeploymentHelper implements Initializable, Disposable, Startable {
	@Inject
	private P2RepositoryFactory factory;

	@Inject
	@Named("xml")
	private DataCompression raw;

	@Inject
	private MavenSession session;

	@Inject
	private Logger logger;

	private LocalDateTime extractP2Timestamp(Path root) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(root, (ClassLoader) null)) {

			CommonP2Repository repo = factory.loadAny(fs.getPath("/"));

			RepositoryFacade<?> artifactRepositoryFacade = repo.getArtifactRepositoryFacade();

			long ts_ms = artifactRepositoryFacade.getTimestamp();

			return LocalDateTime.ofEpochSecond(ts_ms / 1000, (int) (ts_ms % 1000) * 1000000, ZoneOffset.UTC);
		}
	}

	static String getLayout(MavenProject p) {
		Plugin plugin = p.getPlugin("com.github.pms1.tppt:tppt-maven-plugin");

		Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
		Xpp3Dom layout = configuration != null ? configuration.getChild("layout") : null;
		if (layout == null)
			return "@{artifactId}-@{version}-@{timestamp}";
		else
			return layout.getValue();
	}

	public String getPath(MavenProject p) throws MojoExecutionException {
		try {
			return getPath(p, extractP2Timestamp(p.getArtifact().getFile().toPath()));
		} catch (IOException e1) {
			throw new MojoExecutionException("foo", e1);
		}
	}

	public String getPath(MavenProject p, LocalDateTime timestamp) throws MojoExecutionException {
		Preconditions.checkNotNull(p);

		String layout = getLayout(p);
		InterpolatedString layout1 = InterpolatedString.parse(layout);
		Map<String, Object> context = new HashMap<>();
		context.put("group", p.getGroupId());
		context.put("artifactId", p.getArtifactId());
		context.put("version", p.getVersion());
		context.put("timestamp", timestamp);

		return interpolate(layout1, context);
	}

	public RepositoryPathPattern getPathPattern(MavenProject p) {
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

	static String interpolate(InterpolatedString layout1, Map<String, Object> context) {
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

	static private final String defaultFormatterRegexp = "\\d{17}";

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
					String regexp;

					if (variable.size() == 1) {
						formatter = defaultFormatter;
						regexp = defaultFormatterRegexp;
					} else if (variable.size() == 2) {
						formatter = DateTimeFormatter.ofPattern(variable.get(1));
						regexp = asRegularExpression(variable.get(1));
					} else {
						throw new IllegalArgumentException();
					}

					postProcessors.put(variable.get(0), p -> p == null ? null : LocalDateTime.parse(p, formatter));
					b.append("(?<" + variable.get(0) + ">" + regexp + ")");
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

	private Set<String> getFiles(CommonP2Repository repo) throws IOException {
		Set<String> files = new TreeSet<>();

		files.add(P2RepositoryFactory.P2INDEX);

		repo.accept(new P2RepositoryVisitor<Void>() {

			@Override
			public Void visit(P2CompositeRepository repo) {
				for (DataCompression dc : repo.getArtifactDataCompressions())
					files.add(P2IndexType.compositeArtifacts.getFilePrefix() + "." + dc.getFileSuffix());

				for (DataCompression dc : repo.getMetadataDataCompressions())
					files.add(P2IndexType.compositeMetadata.getFilePrefix() + "." + dc.getFileSuffix());

				return null;
			}

			@Override
			public Void visit(P2Repository repo) {
				for (DataCompression dc : repo.getArtifactDataCompressions())
					files.add(P2IndexType.artifacts.getFilePrefix() + "." + dc.getFileSuffix());

				for (DataCompression dc : repo.getMetadataDataCompressions())
					files.add(P2IndexType.metadata.getFilePrefix() + "." + dc.getFileSuffix());

				try {
					ArtifactRepositoryFacade facade;
					facade = repo.getArtifactRepositoryFacade();
					for (ArtifactId e : facade.getArtifacts().keySet())
						files.add(repo.getPath().relativize(facade.getArtifactUri(e)).toString()
								.replace(File.separatorChar, '/'));
				} catch (IOException e1) {
					throw new RuntimeException(e1);
				}

				return null;
			}
		});

		return files;
	}

	private void doInstall(Path sourcePath, Set<String> sourceFiles, Path targetPath, Set<String> targetFiles)
			throws IOException {

		TreeSet<Path> toRemove = new TreeSet<>();

		for (String p : Sets.union(sourceFiles, targetFiles)) {
			Path p1 = sourcePath.resolve(p);
			Path p2 = targetPath.resolve(p);

			logger.info("deploying/createDirectories " + p2.getParent());
			Files.createDirectories(p2.getParent());

			if (sourceFiles.contains(p) && targetFiles.contains(p)) {
				ByteSource bs1 = new PathByteSource(p1);
				ByteSource bs2 = new PathByteSource(p2);

				if (!bs1.contentEquals(bs2)) {
					logger.info("deploying/replace " + p1 + " -> " + p2);
					Files.copy(p1, p2, StandardCopyOption.REPLACE_EXISTING); // StandardCopyOption.COPY_ATTRIBUTES
				} else {
					// System.out.println("UNMODIFIED " + p);
				}
			} else if (sourceFiles.contains(p)) {
				logger.info("deploying/new " + p1 + " -> " + p2);
				Files.copy(p1, p2); // , StandardCopyOption.COPY_ATTRIBUTES
			} else {
				logger.info("deploying/delete " + p1 + " -> " + p2);
				Files.delete(p2);
				toRemove.add(p2.getParent());
			}
		}

		for (Path p : toRemove.descendingSet()) {
			try {
				logger.info("deploying/delete " + p);
				Files.delete(p);
			} catch (DirectoryNotEmptyException e) {
				// that's ok
			}
		}
	}

	public void replace(CommonP2Repository source, CommonP2Repository target) throws IOException {
		Preconditions.checkNotNull(source);
		Preconditions.checkNotNull(target);

		Set<String> f1 = getFiles(source);
		Set<String> f2 = getFiles(target);

		doInstall(source.getPath(), f1, target.getPath(), f2);
	}

	public void install(CommonP2Repository source, Path target) throws IOException {
		Preconditions.checkNotNull(source);
		Preconditions.checkNotNull(target);

		Set<String> f1 = getFiles(source);

		doInstall(source.getPath(), f1, target, Collections.emptySet());

	}

	static String asRegularExpression(String pattern) {
		StringBuilder regex = new StringBuilder();

		for (int i = 0; i != pattern.length();) {
			int start = i;
			char c = pattern.charAt(i++);
			while (i != pattern.length() && pattern.charAt(i) == c)
				++i;
			String part = pattern.substring(start, i);
			switch (part) {
			case "yyyy":
			case "yy":
			case "MM":
			case "dd":
			case "HH":
			case "mm":
			case "ss":
				regex.append("\\d{" + part.length() + "}");
				break;
			default:
				throw new IllegalArgumentException("Unhandled part '" + part + "' of pattern '" + pattern + "'");
			}
		}
		return regex.toString();
	}

	public DeploymentTarget createTarget(DeploymentRepository deploymentTarget) throws MojoExecutionException {

		if (deploymentTarget.uri.getScheme() == null || !deploymentTarget.uri.isAbsolute())
			throw new MojoExecutionException("The deploymentTarget '" + deploymentTarget.uri + "' is not a valid URI.");

		switch (deploymentTarget.uri.getScheme()) {
		case "file":
			Path path = Paths.get(deploymentTarget.uri);
			if (Files.exists(path) && !Files.isDirectory(path))
				throw new MojoExecutionException("The path '" + path + "' already exists and is not a directory");
			try {
				Files.createDirectories(path);
			} catch (IOException e1) {
				throw new MojoExecutionException("Failed to create the path '" + path + "'", e1);
			}
			return new DeploymentTargetImpl(path, factory, raw);
		case "http":
		case "https":
			Sardine sardine = SardineFactory.begin();

			Server server = session.getSettings().getServer(deploymentTarget.serverId);
			if (server != null) {
				if (server.getUsername() != null && server.getPassword() != null)
					sardine.setCredentials(server.getUsername(), server.getPassword());
			}
			sardine.enablePreemptiveAuthentication(deploymentTarget.uri.getHost(), deploymentTarget.uri.getPort(),
					deploymentTarget.uri.getPort());

			VFileSystem fs = new VFileSystem(sardine, deploymentTarget.uri);

			path = new VPath(fs);
			return new DeploymentTargetImpl(path, factory, raw) {
				@Override
				public void close() {
					super.close();
					try {
						sardine.shutdown();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
		default:
			throw new MojoExecutionException("The scheme '" + deploymentTarget.uri.getScheme()
					+ "' of deploymentTarget '" + deploymentTarget + "' is not supported.");
		}
	}

	@Override
	public void start() throws StartingException {
		// System.err.println("LIFE CYCLE " + this + " START");
	}

	@Override
	public void stop() throws StoppingException {
		// System.err.println("LIFE CYCLE " + this + " STOP");
	}

	@Override
	public void dispose() {
		// System.err.println("LIFE CYCLE " + this + " DISPOSE");
	}

	@Override
	public void initialize() throws InitializationException {
		// System.err.println("LIFE CYCLE " + this + " INIT " + getClass() + " " +
		// System.identityHashCode(getClass())
		// + " " + getClass().getClassLoader() +
		// System.identityHashCode(getClass().getClassLoader()));
	}
}
