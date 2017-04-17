package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.manager.ScmManager;

import com.github.pms1.tppt.core.DeploymentHelper;
import com.github.pms1.tppt.p2.CommonP2Repository;
import com.github.pms1.tppt.p2.P2RepositoryFactory;
import com.github.pms1.tppt.p2.RepositoryComparator;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * A maven mojo for creating an index.html of deployed repositories
 * 
 * @author pms1
 **/
@Mojo(name = "create-deployment-index", requiresDependencyResolution = ResolutionScope.COMPILE, executionStrategy = "once-per-session", aggregator = true)
public class CreateDeploymentIndexMojo extends AbstractMojo {

	@Component
	private ScmManager scm;

	@Parameter(property = "session", readonly = true)
	private MavenSession session;

	@Parameter(property = "project", readonly = true)
	private MavenProject project;

	@Parameter(property = "tppt.deploymentTarget", required = true)
	private URI deploymentTarget;

	@Parameter(defaultValue = "${project.basedir}", readonly = true)
	private File basedir;

	@Component
	private DeploymentHelper deployHelp;

	@Component
	private RepositoryComparator repositoryComparator;

	@Component
	private P2RepositoryFactory repositoryFactory;

	@Component
	private DeploymentHelper deploymentHelper;

	public void execute() throws MojoExecutionException, MojoFailureException {
		Path targetPath = new DeploymentTarget(deploymentTarget).getPath();

		try {
			Map<URI, CommonP2Repository> repos = new HashMap<>();

			for (MavenProject p : session.getProjects()) {
				switch (p.getPackaging()) {
				case "tppt-repository":
				case "tppt-composite-repository":
					break;
				default:
					continue;
				}

				if (p.getArtifact() == null)
					throw new MojoExecutionException("The project " + p + " did not create a build artifact");
				if (p.getArtifact().getFile() == null)
					throw new MojoExecutionException(
							"The project " + p + " did not assign a file to the build artifact");

				Path targetRoot = targetPath.resolve(targetPath.resolve(deployHelp.getPath(p)));

				CommonP2Repository repo = repositoryFactory.loadAny(targetRoot);
				if (repo == null)
					throw new MojoExecutionException("No repo found at '" + targetRoot + "'");

				repos.put(deploymentTarget.relativize(targetRoot.toUri()), repo);
			}

			Configuration cfg = new Configuration(Configuration.VERSION_2_3_26);

			cfg.setClassForTemplateLoading(CreateDeploymentIndexMojo.class, "");
			cfg.setDefaultEncoding("UTF-8");
			cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

			Map<String, Object> input = new HashMap<String, Object>();

			input.put("project", project);
			input.put("repositories", repos);

			Template template = cfg.getTemplate("default-index-template.ftlh");

			try (Writer writer = Files.newBufferedWriter(targetPath.resolve("index.html"), StandardCharsets.UTF_8)) {
				template.process(input, writer);
			}
		} catch (IOException | TemplateException e) {
			throw new MojoExecutionException("Execution of create-index failed", e);
		}
	}
}
