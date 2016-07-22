package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.command.add.AddScmResult;
import org.apache.maven.scm.command.checkin.CheckInScmResult;
import org.apache.maven.scm.command.checkout.CheckOutScmResult;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.repository.ScmRepository;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.github.pms1.tppt.InterpolatedString.Visitor;

import de.pdark.decentxml.Attribute;
import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLIOSource;
import de.pdark.decentxml.XMLParser;

/**
 * A maven mojo for "deploying" a p2 repository to the filesystem.
 * 
 * @author pms1
 **/
@Mojo(name = "deploy", requiresDependencyResolution = ResolutionScope.COMPILE, executionStrategy = "once-per-session", aggregator = true)
public class DeployMojo extends AbstractMojo {

	@Component
	private ScmManager scm;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

	@Parameter(property = "tppt.deploymentTarget", required = true)
	private URI deploymentTarget;

	@Parameter(defaultValue = "${project.basedir}", readonly = true)
	private File basedir;

	@Parameter(property = "tppt.scmWorkingDirectory", defaultValue = "${project.build.directory}/install-working-directory")
	private File scmWorkingDirectory;

	// @Parameter(property = "invoker.skip", defaultValue = "false")
	// private boolean skipInvocation;

	Attribute findTimestamp(Document d) {

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

	LocalDateTime extractP2Timestamp(Path p) throws IOException {
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

	String getLayout(MavenProject p) {
		Plugin plugin = p.getPlugin("com.github.pms1.tppt:tppt-maven-plugin");

		Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
		Xpp3Dom layout = configuration.getChild("layout");
		if (layout == null)
			return "@{artifactId}-@{version}-@{timestamp}";
		else
			return layout.getValue();
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (deploymentTarget.getScheme() == null || !deploymentTarget.isAbsolute())
			throw new MojoExecutionException("The deploymentTarget '" + deploymentTarget + "' is not a valid URI.");

		Path targetPath;

		switch (deploymentTarget.getScheme()) {
		case "file":
			targetPath = Paths.get(deploymentTarget);
			if (Files.exists(targetPath) && !Files.isDirectory(targetPath))
				throw new MojoExecutionException("The path '" + targetPath + "' already exists and is not a directory");
			try {
				Files.createDirectories(targetPath);
			} catch (IOException e1) {
				throw new MojoExecutionException("Failed to create the path '" + targetPath + "'", e1);
			}
			break;
		default:
			throw new MojoExecutionException("The scheme '" + deploymentTarget.getScheme() + "' of deploymentTarget '"
					+ deploymentTarget + "' is not supported.");
		}
		getLog().info("Deploying to " + targetPath);

		for (MavenProject p : session.getProjects()) {
			if (!p.getPackaging().equals("tppt-repository"))
				continue;

			if (p.getArtifact() == null)
				throw new MojoExecutionException("The project " + p + " did not create a build artifact");
			if (p.getArtifact().getFile() == null)
				throw new MojoExecutionException("The project " + p + " did not assign a file to the build artifact");

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

			String layout2 = interpolate(layout1, context);

			Path targetRoot = targetPath.resolve(targetPath.resolve(layout2));

			getLog().info("Deploying " + p.getGroupId() + ":" + p.getArtifactId() + ":" + p.getVersion() + " to "
					+ targetRoot);

			try {
				Files.createDirectories(targetRoot.getParent());
				Files.createDirectory(targetRoot);

				try (FileSystem fs = FileSystems.newFileSystem(p.getArtifact().getFile().toPath(), null)) {
					final Path root = fs.getPath("/");

					Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							// need toString as the paths are from different
							// providers
							Path resolve = targetRoot.resolve(root.relativize(file).toString());

							Files.copy(file, resolve, StandardCopyOption.COPY_ATTRIBUTES);
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
								throws IOException {
							// need toString as the paths are from different
							// providers
							Path resolve = targetRoot.resolve(root.relativize(dir).toString());

							if (!Files.exists(resolve))
								Files.createDirectory(resolve);

							return FileVisitResult.CONTINUE;
						}
					});
				}
			} catch (IOException e) {
				throw new MojoExecutionException(
						"Unpacking '" + p.getArtifact().getFile().toPath() + "' to '" + targetPath + "' failed.", e);
			}
		}

		if (true)
			return;

		try {
			ScmRepository repo = scm.makeScmRepository("scm:jgit:file:///c:/temp/foo");
			System.err.println("repo " + repo);

			File workingDirectory = new File("c:/temp/foo-wd");

			CheckOutScmResult result = scm.checkOut(repo, new ScmFileSet(workingDirectory), new ScmBranch("master"));
			System.err.println("result " + result);
			show(result);
			if (!result.isSuccess())
				throw new Error("R " + result);
			String file = System.currentTimeMillis() + "";
			File f = new File(workingDirectory, file);
			Files.newOutputStream(f.toPath()).close();

			AddScmResult add = scm.add(repo, new ScmFileSet(workingDirectory, new File(file)));
			System.err.println("add " + add);
			show(add);
			for (ScmFile f1 : add.getAddedFiles()) {
				System.err.println("f1 " + f1);
			}

			CheckInScmResult in = scm.checkIn(repo, new ScmFileSet(workingDirectory), "test1");
			System.err.println("result " + in);
			show(in);
			for (ScmFile f1 : in.getCheckedInFiles()) {
				System.err.println("f1 " + f1);
			}
		} catch (ScmException e) {
			throw new MojoExecutionException("foo", e);
		} catch (IOException e) {
			throw new MojoExecutionException("foo", e);
		}
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
				if (o == null)
					throw new IllegalArgumentException("No variable: " + variable.get(0));
				String s;
				if (o.getClass() == String.class) {
					if (variable.size() != 1)
						throw new IllegalArgumentException();
					s = o.toString();
				} else if (o.getClass() == LocalDateTime.class) {
					String format;
					if (variable.size() == 1)
						format = "yyyyMMddHHmmssSSS";
					else if (variable.size() == 2)
						format = variable.get(1);
					else
						throw new IllegalArgumentException();
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
					s = formatter.format((LocalDateTime) o);
				} else
					throw new IllegalArgumentException();
				b.append(s);
			}

		});

		return b.toString();
	}

	private void show(ScmResult result) {
		if (!result.isSuccess())
			System.err.println("failed");
		System.err.println("CL " + result.getCommandLine());
		System.err.println("CL " + result.getCommandOutput());
		System.err.println("CL " + result.getProviderMessage());
	}
}
