package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

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

import com.github.pms1.tppt.core.DeploymentHelper;
import com.github.pms1.tppt.p2.P2Repository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.RepositoryComparator;

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

	@Component
	private DeploymentHelper deployHelp;

	@Component
	private RepositoryComparator repositoryComparator;

	@Component
	private P2RepositoryFactory repositoryFactory;

	@Component
	private DeploymentHelper deploymentHelper;

	private void doInstall(Path zip, Path targetRoot) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(zip, null)) {
			P2Repository r1 = repositoryFactory.create(fs.getPath("/"));
			P2Repository r2 = repositoryFactory.create(targetRoot);

			if (r2 == null) {
				deploymentHelper.install(r1, targetRoot);
			} else {
				boolean equal = repositoryComparator.run(r1, r2);
				if (!equal) {
					getLog().info("Replacing existing repository");
					deploymentHelper.replace(r1, r2);
				} else {
					getLog().info("Equal to existing repository, skipping deployment");
				}
			}
		}
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		Path targetPath = new DeploymentTarget(deploymentTarget).getPath();

		for (MavenProject p : session.getProjects()) {
			if (!p.getPackaging().equals("tppt-repository"))
				continue;

			if (p.getArtifact() == null)
				throw new MojoExecutionException("The project " + p + " did not create a build artifact");
			if (p.getArtifact().getFile() == null)
				throw new MojoExecutionException("The project " + p + " did not assign a file to the build artifact");

			Path targetRoot = targetPath.resolve(targetPath.resolve(deployHelp.getPath(p)));
			getLog().info("Deploying to " + targetRoot);

			try {
				doInstall(p.getArtifact().getFile().toPath(), targetRoot);
			} catch (IOException e) {
				throw new MojoExecutionException(
						"Deployment of '" + p.getArtifact().getFile().toPath() + "' to '" + targetPath + "' failed.",
						e);
			}
		}

		if (true)
			return;

		try

		{
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
