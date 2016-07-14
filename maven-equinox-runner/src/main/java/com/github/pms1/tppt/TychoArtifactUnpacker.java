package com.github.pms1.tppt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.FileUtils;

import com.github.pms1.tppt.TychoUnpackLocker.Lock;

@Component(role = TychoArtifactUnpacker.class)
public class TychoArtifactUnpacker {

	@Requirement
	private Logger logger;

	@Requirement(hint = "zip")
	private UnArchiver unArchiver;

	public Path addRuntimeArtifact(MavenSession session, Artifact artifact)
			throws MavenExecutionException, IOException {

		File artifactFile = new File(session.getLocalRepository().getBasedir(),
				session.getLocalRepository().pathOf(artifact));
		File eclipseDir = new File(artifactFile.getParentFile(), "eclipse");

		try (Lock lock = new TychoUnpackLocker().lock(artifactFile.toPath())) {
			if (!eclipseDir.exists() || artifact.isSnapshot()) {
				logger.debug("Extracting Tycho's OSGi runtime");

				if (artifact.getFile().lastModified() > eclipseDir.lastModified()) {
					logger.debug("Unpacking Tycho's OSGi runtime to " + eclipseDir);
					try {
						FileUtils.deleteDirectory(eclipseDir);
					} catch (IOException e) {
						logger.warn("Failed to delete Tycho's OSGi runtime " + eclipseDir + ": " + e.getMessage());
					}
					unArchiver.setSourceFile(artifact.getFile());
					unArchiver.setDestDirectory(eclipseDir.getParentFile());
					try {
						unArchiver.extract();
					} catch (ArchiverException e) {
						throw new MavenExecutionException("Failed to unpack Tycho's OSGi runtime: " + e.getMessage(),
								e);
					}

					eclipseDir.setLastModified(artifact.getFile().lastModified());
				}
			}
		}

		return eclipseDir.toPath();
	}
}
