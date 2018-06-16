package com.github.pms1.tppt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.osgi.internal.framework.EquinoxConfiguration.ConfigValues;
import org.eclipse.osgi.internal.framework.EquinoxContainer;
import org.eclipse.osgi.internal.location.BasicLocation;
import org.eclipse.osgi.service.datalocation.Location;

public class TychoUnpackLocker {

	private static final String LOCKFILE_SUFFIX = ".tycholock";

	static interface Lock extends AutoCloseable {
		public void close();
	}

	private final static Location anyLocation = new BasicLocation(null, null, false, null,
			new ConfigValues(new HashMap<>(), new HashMap<>()), new EquinoxContainer(null), new AtomicBoolean(false));

	@SuppressWarnings("deprecation")
	public Lock lock(Path p) throws IOException {
		// don't know if this actually makes sense, but the replacement for
		// getCanonicalFile in Path requires the file to exist
		Path lockMarkerFile = p.getParent().resolve(p.getFileName().toString() + LOCKFILE_SUFFIX).toFile()
				.getCanonicalFile().toPath();

		if (Files.isDirectory(lockMarkerFile))
			throw new RuntimeException("Lock marker file " + lockMarkerFile + " already exists and is a directory");

		Files.createDirectories(lockMarkerFile.getParent());

		Location lockFileLocation = anyLocation.createLocation(null, null, false);
		// use deprecated API to ensure compatibility with tycho
		lockFileLocation.set(lockMarkerFile.toFile().toURL(), false,
				lockMarkerFile.toAbsolutePath().toFile().toString());

		if (!lockFileLocation.lock())
			return null;

		return new Lock() {
			@Override
			public void close() {
				lockFileLocation.release();

				try {
					Files.deleteIfExists(lockMarkerFile);
				} catch (IOException e) {
					lockMarkerFile.toFile().deleteOnExit();
				}
			}

		};
	}

}
