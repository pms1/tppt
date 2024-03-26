package com.github.pms1.tppt;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class M3 {

	public static void main(String[] args) throws Exception {

		Path root = Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/");

		List<AppBundle> bundles = new ArrayList<>();

		bundles.add(new AppBundle(root.resolve("org.eclipse.equinox.common_3.19.0.v20240214-0846.jar").toUri().toURL(),
				2, true));

//		ch.qos.logback.classic,1.5.0,plugins/ch.qos.logback.classic_1.5.0.jar,2,true
//		org.apache.aries.spifly.dynamic.bundle,1.3.7,plugins/org.apache.aries.spifly.dynamic.bundle_1.3.7.jar,2,true
//		org.apache.felix.scr,2.2.10,plugins/org.apache.felix.scr_2.2.10.jar,2,true
//		org.eclipse.core.runtime,3.31.0.v20240215-1631,plugins/org.eclipse.core.runtime_3.31.0.v20240215-1631.jar,4,true
//		org.eclipse.equinox.common,3.19.0.v20240214-0846,plugins/org.eclipse.equinox.common_3.19.0.v20240214-0846.jar,2,true
//		org.eclipse.equinox.event,1.7.0.v20240214-0846,plugins/org.eclipse.equinox.event_1.7.0.v20240214-0846.jar,2,true
//		org.eclipse.equinox.p2.reconciler.dropins,1.5.300.v20240212-0924,plugins/org.eclipse.equinox.p2.reconciler.dropins_1.5.300.v20240212-0924.jar,4,true
//		org.eclipse.equinox.simpleconfigurator,1.5.200.v20240209-1053,plugins/org.eclipse.equinox.simpleconfigurator_1.5.200.v20240209-1053.jar,1,true
//		org.eclipse.osgi,3.19.0.v20240213-1246,plugins/org.eclipse.osgi_3.19.0.v20240213-1246.jar,-1,true

		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.apache.felix.scr_2.2.10.jar").toUri().toURL(), 2,
				true));

		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.core.runtime_3.31.0.v20240215-1631.jar")
						.toUri().toURL(),
				4, true));

		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.osgi.service.component_1.5.1.202212101352.jar").toUri()
						.toURL(),
				4, false));
		bundles.add(new AppBundle(Path
				.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.osgi.util.promise_1.3.0.202212101352.jar").toUri().toURL(),
				4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.osgi.util.function_1.2.0.202109301733.jar").toUri()
						.toURL(),
				4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.core.jobs_3.15.200.v20231214-1526.jar").toUri()
						.toURL(),
				4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.registry_3.12.0.v20240213-1057.jar")
						.toUri().toURL(),
				4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.preferences_3.11.0.v20240210-0844.jar")
						.toUri().toURL(),
				4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.osgi.service.prefs_1.1.2.202109301733.jar").toUri()
						.toURL(),
				4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.core.contenttype_3.9.300.v20231218-0909.jar")
						.toUri().toURL(),
				4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.app_1.7.0.v20240213-1427.jar").toUri()
						.toURL(),
				4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.publisher.eclipse_1.6.0.v20240229-1022.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path
				.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.frameworkadmin_2.3.100.v20240201-0843.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.frameworkadmin.equinox_1.3.100.v20240213-1609.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.artifact.repository_1.5.300.v20240220-1431.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/bcpg_1.77.0.jar").toUri().toURL(), 4,
				false));
		bundles.add(new AppBundle(Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/bcprov_1.77.0.jar").toUri().toURL(), 4,
				false));
		bundles.add(new AppBundle(Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/bcutil_1.77.0.jar").toUri().toURL(), 4,
				false));
		bundles.add(new AppBundle(Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/bcpkix_1.77.0.jar").toUri().toURL(), 4,
				false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.core_2.11.0.v20240210-1628.jar")
						.toUri().toURL(),
				4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.jarprocessor_1.3.300.v20240201-0843.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.metadata_2.9.0.v20240213-1100.jar")
						.toUri().toURL(),
				4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.metadata.repository_1.5.300.v20240201-0843.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.artifact.repository_1.5.300.v20240220-1431.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path
				.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.repository_2.8.100.v20240207-1113.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.security_1.4.200.v20240213-1244.jar")
						.toUri().toURL(),
				4, false));
		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.tukaani.xz_1.9.0.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path
				.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.p2.publisher_1.9.100.v20240212-1707.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.simpleconfigurator.manipulator_2.3.100.v20240201-0843.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.simpleconfigurator_1.5.200.v20240209-1053.jar")
				.toUri().toURL(), 4, false));
		bundles.add(new AppBundle(Path.of(
				"W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.osgi.compatibility.state_1.2.1000.v20240213-1057.jar")
				.toUri().toURL(), 4, false));

		bundles.add(new AppBundle(Path.of(
				"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/com/github/pms1/tppt/tppt-mirror-application/0.5.0-SNAPSHOT/tppt-mirror-application-0.5.0-SNAPSHOT.jar")
				.toUri().toURL(), 4, false));

		if (false) {
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/jakarta/xml/bind/jakarta.xml.bind-api/4.0.0/jakarta.xml.bind-api-4.0.0.jar")
					.toUri().toURL(), 4, false));
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/jakarta/activation/jakarta.activation-api/2.1.3/jakarta.activation-api-2.1.3.jar")
					.toUri().toURL(), 4, false));
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/org/glassfish/jaxb/jaxb-runtime/4.0.5/jaxb-runtime-4.0.5.jar")
					.toUri().toURL(), 4, false));
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/org/glassfish/jaxb/txw2/4.0.5/txw2-4.0.5.jar")
					.toUri().toURL(), 4, false));
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/com/sun/istack/istack-commons-runtime/4.2.0/istack-commons-runtime-4.2.0.jar")
					.toUri().toURL(), 4, false));
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/org/apache/httpcomponents/httpclient-osgi/4.5.14/httpclient-osgi-4.5.14.jar")
					.toUri().toURL(), 4, false));
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/org/apache/httpcomponents/httpcore-osgi/4.4.16/httpcore-osgi-4.4.16.jar")
					.toUri().toURL(), 4, false));
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/org/osgi/org.osgi.service.cm/1.6.1/org.osgi.service.cm-1.6.1.jar")
					.toUri().toURL(), 4, false));
			bundles.add(new AppBundle(Path.of(
					"W:/work/workspaces/tppt/git/tppt/tppt-maven-plugin/target/local-repo/commons-logging/commons-logging/1.2/commons-logging-1.2.jar")
					.toUri().toURL(), 4, false));
		}

		//

		if (false)
			bundles.add(new AppBundle(Path.of(
					"C:/Users/Mirko/.m2/repository/org/eclipse/angus/angus-activation/2.0.2/angus-activation-2.0.2.jar")
					.toUri().toURL(), 4, false));

		bundles.add(new AppBundle(
				root.resolve("org.eclipse.equinox.p2.director_2.6.300.v20240207-1113.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(
				root.resolve("org.eclipse.equinox.p2.engine_2.10.0.v20240210-0918.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(root.resolve("org.sat4j.core_2.3.6.v20201214.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(root.resolve("org.sat4j.pb_2.3.6.v20201214.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(
				root.resolve("org.eclipse.equinox.p2.repository.tools_2.4.300.v20240207-1113.jar").toUri().toURL(), 4,
				false));

		bundles.add(new AppBundle(root.resolve("org.objectweb.asm_9.6.0.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(root.resolve("org.objectweb.asm.commons_9.6.0.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(root.resolve("org.objectweb.asm.util_9.6.0.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(root.resolve("org.objectweb.asm.tree_9.6.0.jar").toUri().toURL(), 4, false));
		bundles.add(new AppBundle(root.resolve("org.objectweb.asm.tree.analysis_9.6.0.jar").toUri().toURL(), 4, false));

		bundles.add(new AppBundle(
				Path.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.apache.aries.spifly.dynamic.bundle_1.3.7.jar").toUri()
						.toURL(),
				2, true));

		AppRunnerConfig config = new AppRunnerConfig(
				root.resolve("org.eclipse.osgi_3.19.0.v20240213-1246.jar").toUri().toURL(), bundles);

		new EmbeddedEquinoxAppRunner(config).run("tppt-mirror-application.id1",
				"rO0ABXNyACZjb20uZ2l0aHViLnBtczEudHBwdC5taXJyb3IuTWlycm9yU3BlYwGXnI+2qvzcAgAMTAAJYWxnb3JpdGhtdAA2TGNvbS9naXRodWIvcG1zMS90cHB0L21pcnJvci9NaXJyb3JTcGVjJEFsZ29yaXRobVR5cGU7WwAKZXhjbHVkZUl1c3QAE1tMamF2YS9sYW5nL1N0cmluZztbAAdmaWx0ZXJzdAAQW0xqYXZhL3V0aWwvTWFwO1sAA2l1c3EAfgACTAAQbWlycm9yUmVwb3NpdG9yeXQADkxqYXZhL25ldC9VUkk7TAAHbWlycm9yc3QAD0xqYXZhL3V0aWwvTWFwO0wAB29mZmxpbmV0ADRMY29tL2dpdGh1Yi9wbXMxL3RwcHQvbWlycm9yL01pcnJvclNwZWMkT2ZmbGluZVR5cGU7TAAFcHJveHl0AChMY29tL2dpdGh1Yi9wbXMxL3RwcHQvbWlycm9yL2pheGIvUHJveHk7WwAHc2VydmVyc3QAOltMY29tL2dpdGh1Yi9wbXMxL3RwcHQvbWlycm9yL01pcnJvclNwZWMkQXV0aGVudGljYXRlZFVyaTtbABJzb3VyY2VSZXBvc2l0b3JpZXN0ADpbTGNvbS9naXRodWIvcG1zMS90cHB0L21pcnJvci9NaXJyb3JTcGVjJFNvdXJjZVJlcG9zaXRvcnk7TAAFc3RhdHN0ADJMY29tL2dpdGh1Yi9wbXMxL3RwcHQvbWlycm9yL01pcnJvclNwZWMkU3RhdHNUeXBlO0wAEHRhcmdldFJlcG9zaXRvcnlxAH4ABHhwfnIANGNvbS5naXRodWIucG1zMS50cHB0Lm1pcnJvci5NaXJyb3JTcGVjJEFsZ29yaXRobVR5cGUAAAAAAAAAABIAAHhyAA5qYXZhLmxhbmcuRW51bQAAAAAAAAAAEgAAeHB0ABBwZXJtaXNzaXZlU2xpY2VydXIAE1tMamF2YS5sYW5nLlN0cmluZzut0lbn6R17RwIAAHhwAAAAAXQAK29yZy5lY2xpcHNlLmNvcmUuam9icy8zLjE0LjAudjIwMjMwMzE3LTA5MDF1cgAQW0xqYXZhLnV0aWwuTWFwO//gsIbqR0wLAgAAeHAAAAAAdXEAfgAQAAAAAXQALm9yZy5lY2xpcHNlLmVxdWlub3guY29yZS5mZWF0dXJlLmZlYXR1cmUuZ3JvdXBzcgAMamF2YS5uZXQuVVJJrAF4LkOeSasDAAFMAAZzdHJpbmd0ABJMamF2YS9sYW5nL1N0cmluZzt4cHQAW2ZpbGU6Ly8vVzovd29yay93b3Jrc3BhY2VzL3RwcHQvZ2l0L3RwcHQvdHBwdC1tYXZlbi1wbHVnaW4vdGFyZ2V0L2xvY2FsLXJlcG8vLmNhY2hlL3RwcHQvcDJ4c3IAF2phdmEudXRpbC5MaW5rZWRIYXNoTWFwNMBOXBBswPsCAAFaAAthY2Nlc3NPcmRlcnhyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAB3CAAAABAAAAAAeAB+cgAyY29tLmdpdGh1Yi5wbXMxLnRwcHQubWlycm9yLk1pcnJvclNwZWMkT2ZmbGluZVR5cGUAAAAAAAAAABIAAHhxAH4ADXQABm9ubGluZXB1cgA6W0xjb20uZ2l0aHViLnBtczEudHBwdC5taXJyb3IuTWlycm9yU3BlYyRBdXRoZW50aWNhdGVkVXJpO9OWT0zQlXvxAgAAeHAAAAAAdXIAOltMY29tLmdpdGh1Yi5wbXMxLnRwcHQubWlycm9yLk1pcnJvclNwZWMkU291cmNlUmVwb3NpdG9yeTtC98WXe2/DoQIAAHhwAAAAAXNyADdjb20uZ2l0aHViLnBtczEudHBwdC5taXJyb3IuTWlycm9yU3BlYyRTb3VyY2VSZXBvc2l0b3J5FV5K6hTKAmYCAAJMAAx1cGRhdGVQb2xpY3lxAH4AGEwAA3VyaXEAfgAEeHBwc3EAfgAXdAA7aHR0cHM6Ly9kb3dubG9hZC5lY2xpcHNlLm9yZy9yZWxlYXNlcy8yMDIzLTA2LzIwMjMwNjE0MTAwMC94fnIAMGNvbS5naXRodWIucG1zMS50cHB0Lm1pcnJvci5NaXJyb3JTcGVjJFN0YXRzVHlwZQAAAAAAAAAAEgAAeHEAfgANdAAHY29sbGVjdHNxAH4AF3QAXmZpbGU6Ly8vVzovd29yay93b3Jrc3BhY2VzL3RwcHQvZ2l0L3RwcHQvdHBwdC1tYXZlbi1wbHVnaW4vdGFyZ2V0L2l0L21pcnJvci90YXJnZXQvcmVwb3NpdG9yeS94");
		if (false) {
			Path launcher = Path
					.of("W:/fis/ide/fa-23-12/eclipse/plugins/org.eclipse.equinox.launcher_1.6.700.v20240213-1244.jar");

			new ProcessEquinoxAppRunner(config, launcher, Map.of()).run((InputStream) null,
					"org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher");
		}

	}
}
