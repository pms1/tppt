package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.maven.execution.MavenSession;
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

	private final DeploymentHelper deployHelp = new DeploymentHelper();

	public void execute() throws MojoExecutionException, MojoFailureException {
		Path targetPath = new DeploymentTarget(deploymentTarget).getPath();
		getLog().info("Deploying to " + targetPath);

		for (MavenProject p : session.getProjects()) {
			if (!p.getPackaging().equals("tppt-repository"))
				continue;

			if (p.getArtifact() == null)
				throw new MojoExecutionException("The project " + p + " did not create a build artifact");
			if (p.getArtifact().getFile() == null)
				throw new MojoExecutionException("The project " + p + " did not assign a file to the build artifact");

			Path targetRoot = targetPath.resolve(targetPath.resolve(deployHelp.getPath(p)));

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

	private void show(ScmResult result) {
		if (!result.isSuccess())
			System.err.println("failed");
		System.err.println("CL " + result.getCommandLine());
		System.err.println("CL " + result.getCommandOutput());
		System.err.println("CL " + result.getProviderMessage());
	}
}
