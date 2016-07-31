package com.github.pms1.tppt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

public class EquinoxRunner {

	static class CopyThread extends Thread {
		private final InputStream is;
		private final OutputStream os;

		CopyThread(InputStream is, OutputStream os) {
			this.is = is;
			this.os = os;
		}

		@Override
		public void run() {
			try {
				for (;;) {
					int c;
					c = is.read();
					if (c == -1)
						break;
					os.write(c);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	static class Plugin {
		Path p;
		public String id;

	}

	static Plugin scanPlugin(Path p) throws IOException, BundleException {
		Map<String, String> manifest = null;

		try (ZipFile zf = new ZipFile(p.toFile())) {
			ZipEntry entry = zf.getEntry("META-INF/MANIFEST.MF");
			if (entry == null)
				return null;

			manifest = ManifestElement.parseBundleManifest(zf.getInputStream(entry), null);
		}

		ManifestElement[] elements = ManifestElement.parseHeader(Constants.BUNDLE_SYMBOLICNAME,
				manifest.get(Constants.BUNDLE_SYMBOLICNAME));
		if (elements.length != 1)
			throw new IllegalArgumentException();
		Plugin result = new Plugin();
		result.id = elements[0].getValue();

		elements = ManifestElement.parseHeader(Constants.BUNDLE_VERSION, manifest.get(Constants.BUNDLE_VERSION));
		if (elements.length != 1)
			throw new IllegalArgumentException();

		result.p = p;

		return result;
	}

	static String toUri(Path p) {
		// return "file:" + p.toString().replace('\\', '/');
		return p.toUri().toString();
	}

	private final Set<Plugin> plugins;

	EquinoxRunner(Set<Plugin> plugins) {
		this.plugins = plugins;
	}

	public int run(String... args) throws IOException, InterruptedException {

		Path temp = Files.createTempDirectory("com.github.pms1.tppt");

		Path configuration = temp.resolve("configuration");

		Files.createDirectory(configuration);

		// p.put("","
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.core.runtime_3.12.0.v20160222-1238.jar@4\:start");
		Properties p = new Properties();
		p.put("osgi.configuration.cascaded", "false");
		p.put("osgi.install.area", toUri(temp));
		p.put("osgi.bundles.defaultStartLevel", "4");
		p.put("osgi.bundles", plugins.stream().map(p1 -> {

			Integer level;

			switch (p1.id) {
			case "org.eclipse.equinox.common":
				level = 2;
				break;
			case "org.eclipse.core.runtime":
				level = 4;
				break;
			case "org.eclipse.equinox.simpleconfigurator":
				level = 1;
				break;
			case "org.eclipse.update.configurator":
				level = 3;
				break;
			case "org.eclipse.osgi":
				level = -1;
				break;
			case "org.eclipse.equinox.ds":
				level = 1;
				break;
			default:
				level = null;
				break;
			}
			return "reference:" + toUri(p1.p) + (level != null ? "@" + level + ":start" : "");
		}).collect(Collectors.joining(",")));
		p.put("osgi.framework", toUri(getPlugin(plugins, "org.eclipse.osgi").p));
		Path configIni = configuration.resolve("config.ini");
		try (OutputStream out = Files.newOutputStream(configIni)) {
			p.store(out, null);
		}

		List<String> command = new ArrayList<>();
		command.add(Paths.get(System.getProperty("java.home")).resolve("bin/java").toString());
		command.add("-jar");
		command.add(getPlugin(plugins, "org.eclipse.equinox.launcher").p.toString());
		command.add("-configuration");
		command.add(configuration.toString());
		// command.add("-debug");
		// command.add("-consoleLog");
		command.add("-nosplash");
		Collections.addAll(command, args);
		System.err.println(command);
		Process pr = new ProcessBuilder(command).directory(temp.toFile()).start();
		pr.getOutputStream().close();
		CopyThread stdout = new CopyThread(pr.getInputStream(), System.out);
		stdout.start();
		CopyThread stderr = new CopyThread(pr.getErrorStream(), System.err);
		stderr.start();
		int exitCode = pr.waitFor();
		stdout.join();
		stderr.join();

		return exitCode;
		// ,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.filetransfer_5.0.0.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.identity_3.7.0.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.provider.filetransfer.httpclient4.ssl_1.1.0.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.provider.filetransfer.httpclient4_1.1.100.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.provider.filetransfer.ssl_1.0.0.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.provider.filetransfer_3.2.200.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.ssl_1.2.0.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf_3.8.0.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.app_1.3.400.v20150715-1528.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.common_3.8.0.v20160315-1450.jar@2\:start,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.concurrent_1.1.0.v20130327-1442.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.ds_1.4.400.v20160226-2036.jar@1\:start,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.frameworkadmin.equinox_1.0.700.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.frameworkadmin_2.0.200.v20150423-1455.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.launcher_1.3.200.v20151021-1308.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.artifact.repository_1.1.500.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.core_2.4.100.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.director.app_1.0.500.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.director_2.3.200.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.engine_2.4.100.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.garbagecollector_1.0.200.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.jarprocessor_1.0.400.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.metadata.repository_1.2.300.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.metadata_2.3.100.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.publisher.eclipse_1.2.0.v20151011-0147.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.publisher_1.4.0.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.repository.tools_2.1.300.v20151116-0825.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.repository_2.3.200.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.touchpoint.eclipse_2.1.400.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.touchpoint.natives_1.2.100.v20160104-2129.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.transport.ecf_1.1.100.v20150521-1342.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.updatesite_1.0.500.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.preferences_3.6.0.v20160120-1756.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.registry_3.6.100.v20160223-2218.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.security_1.2.200.v20150715-1528.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.simpleconfigurator.manipulator_2.0.100.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.simpleconfigurator_1.1.100.v20150907-2149.jar@1\:start,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.util_1.0.500.v20130404-1337.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.osgi.compatibility.state_1.0.100.v20150709-1617.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.osgi.services_3.5.0.v20150714-1510.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.tycho.noopsecurity_0.25.0.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.sat4j.core_2.3.5.v201308161310.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.sat4j.pb_2.3.5.v201404071733.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.tukaani.xz_1.3.0.v201308270617.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/org.eclipse.tycho.p2.resolver.impl/0.25.0/org.eclipse.tycho.p2.resolver.impl-0.25.0.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/org.eclipse.tycho.p2.maven.repository/0.25.0/org.eclipse.tycho.p2.maven.repository-0.25.0.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/org.eclipse.tycho.p2.tools.impl/0.25.0/org.eclipse.tycho.p2.tools.impl-0.25.0.jar
		// #Sat Jul 09 08:58:59 CEST 2016
		// osgi.bundles=reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.apache.commons.codec_1.6.0.v201305230611.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.apache.commons.logging_1.1.1.v201101211721.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.apache.httpcomponents.httpclient_4.3.6.v201511171540.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.apache.httpcomponents.httpcore_4.3.3.v201411290715.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.core.contenttype_3.5.100.v20160310-1346.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.core.jobs_3.8.0.v20160209-0147.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.core.net_1.2.300.v20141118-1725.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.core.runtime_3.12.0.v20160222-1238.jar@4\:start,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.filetransfer_5.0.0.v20160312-0656.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.identity_3.7.0.v20160312-0656.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.provider.filetransfer.httpclient4.ssl_1.1.0.v20160312-0656.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.provider.filetransfer.httpclient4_1.1.100.v20160312-0656.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.provider.filetransfer.ssl_1.0.0.v20160312-0656.jar,
		// reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.provider.filetransfer_3.2.200.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf.ssl_1.2.0.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.ecf_3.8.0.v20160312-0656.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.app_1.3.400.v20150715-1528.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.common_3.8.0.v20160315-1450.jar@2\:start,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.concurrent_1.1.0.v20130327-1442.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.ds_1.4.400.v20160226-2036.jar@1\:start,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.frameworkadmin.equinox_1.0.700.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.frameworkadmin_2.0.200.v20150423-1455.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.launcher_1.3.200.v20151021-1308.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.artifact.repository_1.1.500.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.core_2.4.100.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.director.app_1.0.500.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.director_2.3.200.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.engine_2.4.100.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.garbagecollector_1.0.200.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.jarprocessor_1.0.400.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.metadata.repository_1.2.300.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.metadata_2.3.100.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.publisher.eclipse_1.2.0.v20151011-0147.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.publisher_1.4.0.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.repository.tools_2.1.300.v20151116-0825.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.repository_2.3.200.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.touchpoint.eclipse_2.1.400.v20160102-2223.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.touchpoint.natives_1.2.100.v20160104-2129.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.transport.ecf_1.1.100.v20150521-1342.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.p2.updatesite_1.0.500.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.preferences_3.6.0.v20160120-1756.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.registry_3.6.100.v20160223-2218.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.security_1.2.200.v20150715-1528.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.simpleconfigurator.manipulator_2.0.100.v20150907-2149.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.simpleconfigurator_1.1.100.v20150907-2149.jar@1\:start,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.equinox.util_1.0.500.v20130404-1337.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.osgi.compatibility.state_1.0.100.v20150709-1617.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.osgi.services_3.5.0.v20150714-1510.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.tycho.noopsecurity_0.25.0.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.sat4j.core_2.3.5.v201308161310.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.sat4j.pb_2.3.5.v201404071733.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.tukaani.xz_1.3.0.v201308270617.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/org.eclipse.tycho.p2.resolver.impl/0.25.0/org.eclipse.tycho.p2.resolver.impl-0.25.0.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/org.eclipse.tycho.p2.maven.repository/0.25.0/org.eclipse.tycho.p2.maven.repository-0.25.0.jar,reference\:file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/org.eclipse.tycho.p2.tools.impl/0.25.0/org.eclipse.tycho.p2.tools.impl-0.25.0.jar
		// osgi.bundlefile.limit=100
		// osgi.bundles.defaultStartLevel=4
		// osgi.install.area=file\:C\:/Users/Mirko/tycho-p2-runtime417198553879940088.tmp
		// osgi.framework=file\:W\:/work/workspaces/i3/workspace-luna/zzz-p2i/target/local-repo/org/eclipse/tycho/tycho-bundles-external/0.25.0/eclipse/plugins/org.eclipse.osgi_3.11.0.v20160309-1913.jar
		// osgi.configuration.cascaded=false

		// D:\Program Files\Java\jdk1.8.0_31\jre\bin\java.exe
		// -jar
		// W:\work\workspaces\i3\workspace-luna\zzz-p2i\target\local-repo\org\eclipse\tycho\tycho-bundles-external\0.25.0\eclipse\plugins\org.eclipse.equinox.launcher_1.3.200.v20151021-1308.jar
		// -configuration
		// C:\Users\Mirko\tycho-p2-runtime5171328063195507911.tmp\configuration
		// -nosplash
		// -application
		// org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher
	}

	private static Plugin getPlugin(Set<Plugin> plugins, String string) {

		List<Plugin> collect = plugins.stream().filter(p -> p.id.equals(string)).collect(Collectors.toList());

		switch (collect.size()) {
		case 1:
			return collect.get(0);
		case 0:
			return null;
		default:
			throw new Error();
		}
	}

}
