package com.github.pms1.tppt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import org.eclipse.core.runtime.internal.adaptor.EclipseAppLauncher;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.service.environment.EnvironmentInfo;
import org.eclipse.osgi.service.runnable.ApplicationLauncher;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;

public class M2 {

	static AppRunnerConfig deserialize(byte[] serializedConfig) {
		try (InputStream is = new ByteArrayInputStream(serializedConfig);
				ObjectInputStream ois = new ObjectInputStream(is)) {

			return (AppRunnerConfig) ois.readObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	static void logFrameworkEvent(FrameworkEvent event) {
		switch (event.getType()) {
		case FrameworkEvent.ERROR:
			System.err.println("[ERROR] " + event.getBundle());
			if (event.getThrowable() != null)
				event.getThrowable().printStackTrace(System.err);
			break;
		case FrameworkEvent.STARTED:
		case FrameworkEvent.STARTLEVEL_CHANGED:
			break;
		default:
			System.err.println("FE " + event.getType() + " " + event);
		}
	}

	public static int run(byte[] serializedConfig, String app, String... args)
			throws BundleException, InterruptedException, Exception {
		AppRunnerConfig config = deserialize(serializedConfig);

		ServiceLoader<ConnectFrameworkFactory> loader = ServiceLoader.load(ConnectFrameworkFactory.class,
				M2.class.getClassLoader());
		ConnectFrameworkFactory factory = loader.findFirst().orElseThrow(() -> new NoSuchElementException());

		Path tempDir = Files.createTempDirectory("maven-equinox-runner-");

		try {
			/*
			 * Mimic directory structure of eclipse, we have seen p2 write to ../p2 in some
			 * cases.
			 */
			Path storage = tempDir.resolve("configuration");
			Files.createDirectory(storage);

			Map<String, String> frameworkConfiguration = new HashMap<>();
			frameworkConfiguration.put(Constants.FRAMEWORK_STORAGE, storage.toString());
			frameworkConfiguration.put(Constants.FRAMEWORK_STORAGE_CLEAN, "true");

			/*
			 * p2 does not import org.xml.sax explicitly, so this must be set.
			 */
			frameworkConfiguration.put(EquinoxConfiguration.PROP_COMPATIBILITY_BOOTDELEGATION, "true");

			frameworkConfiguration.put("eclipse.application", app);

			frameworkConfiguration.put("osgi.install.area", tempDir.toUri().toURL().toString());

			Framework framework = factory.newFramework(frameworkConfiguration, null);
			framework.init(M2::logFrameworkEvent);

			framework.getBundleContext().addFrameworkListener(M2::logFrameworkEvent);
			framework.start();

			try {

				BundleContext context = framework.getBundleContext();
				context.addBundleListener(new BundleListener() {

					@Override
					public void bundleChanged(BundleEvent event) {
						if (true)
							return;

						String type;
						switch (event.getType()) {
						case BundleEvent.RESOLVED:
							type = "RESOLVED";
							break;
						case BundleEvent.STARTED:
							type = "STARTED";
							break;
						default:
							type = Integer.toString(event.getType());
						}

						System.err.println("BE " + type + " " + event + " " + event.getSource() + " "
								+ event.getBundle() + " " + event.getOrigin());

					}
				});

				List<Bundle> autoStart = new ArrayList<>();
				for (AppBundle b1 : config.bundles()) {
					Bundle bundle;

					try (InputStream is = b1.path().openStream()) {
						bundle = context.installBundle(b1.path().toString(), is);
						if (bundle == null)
							throw new RuntimeException("Failed installation: " + b1.path());
					}

					if (b1.startLevel() != null)
						bundle.adapt(BundleStartLevel.class).setStartLevel(b1.startLevel());
					if (b1.autoStart())
						autoStart.add(bundle);
				}

				/*
				 * Start bundles and wait for it to be done
				 */
				CountDownLatch cdl = new CountDownLatch(1);
				framework.adapt(FrameworkStartLevel.class).setStartLevel(6, new FrameworkListener() {

					@Override
					public void frameworkEvent(FrameworkEvent event) {
						// TODO: check ERROR
						if (event.getType() == FrameworkEvent.STARTLEVEL_CHANGED) {
							cdl.countDown();
						} else {
							System.err.println("FE2 " + event);
						}

					}

				});
				cdl.await();

				ServiceReference<EnvironmentInfo> configRef = context.getServiceReference(EnvironmentInfo.class);
				EquinoxConfiguration equinoxConfig = (EquinoxConfiguration) context.getService(configRef);
				equinoxConfig.setAppArgs(args);

				for (Bundle b1 : autoStart)
					b1.start();

				ServiceReference<FrameworkLog> logRef = context.getServiceReference(FrameworkLog.class);
				FrameworkLog log = context.getService(logRef);

				boolean failOnNoDefault = true;
				boolean relaunch = false;

				EclipseAppLauncher appLauncher = new EclipseAppLauncher(context, relaunch, failOnNoDefault, log,
						equinoxConfig);
				ServiceRegistration<?> appLauncherRegistration = context
						.registerService(ApplicationLauncher.class.getName(), appLauncher, null);

				int exitCode;

				try {
					Object result;
					result = appLauncher.start(args);

					if (result instanceof Integer)
						exitCode = (Integer) result;
					else
						exitCode = 0;
				} finally {
					appLauncherRegistration.unregister();
				}

				return exitCode;
			} finally {
				framework.stop();
				Duration wait = Duration.ofSeconds(10);
				FrameworkEvent event = framework.waitForStop(wait.toMillis());
				if (event.getType() != FrameworkEvent.STOPPED) {
					System.err.println("[WARNING] Framework failed to stop after " + wait + " (eventType "
							+ event.getType() + ")");
				}
			}
		} finally {
			try (Stream<Path> s = Files.walk(tempDir)) {
				s.sorted(Comparator.reverseOrder()) //
						.map(Path::toFile) //
						.forEach(File::delete);
			}
		}
	}

}
