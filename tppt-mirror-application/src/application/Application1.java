package application;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.xml.bind.JAXB;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.director.PermissiveSlicer;
import org.eclipse.equinox.internal.p2.repository.Transport;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.internal.repository.mirroring.IArtifactMirrorLog;
import org.eclipse.equinox.p2.internal.repository.mirroring.Mirroring;
import org.eclipse.equinox.p2.internal.repository.tools.MirrorApplication;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;
import org.eclipse.equinox.p2.internal.repository.tools.SlicingOptions;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.osgi.framework.ServiceReference;

public class Application1 implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		Object args = context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		System.err.println("Application1.start " + Arrays.asList((String[]) args));

		for (String s : Arrays.asList((String[]) args)) {
			MirrorSpec ms;
			if (s.equals("-")) {
				if (false)
					for (;;) {
						int c = System.in.read();
						System.err.println("XX " + c);
						if (c == -1)
							return null;
					}
				System.err.println("Application1.reading");
				ms = JAXB.unmarshal(System.in, MirrorSpec.class);
				System.err.println("Application1.read");
			} else {
				try (InputStream is = Files.newInputStream(Paths.get(s))) {
					ms = JAXB.unmarshal(is, MirrorSpec.class);
				}
			}
			System.err.println("Application1.MS " + ms);
			System.err.println("Application1.IUS " + Arrays.toString(ms.ius));
		}

		IProgressMonitor monitor = new IProgressMonitor() {

			@Override
			public void worked(int work) {
				// TODO Auto-generated method stub

			}

			@Override
			public void subTask(String name) {
				System.err.println("ST " + name);

			}

			@Override
			public void setTaskName(String name) {
				System.err.println("STN " + name);
			}

			@Override
			public void setCanceled(boolean value) {
				// TODO Auto-generated method stub

			}

			@Override
			public boolean isCanceled() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void internalWorked(double work) {
				// TODO Auto-generated method stub

			}

			@Override
			public void done() {
				System.err.println("DONE");
			}

			@Override
			public void beginTask(String name, int totalWork) {
				System.err.println("BT " + name);

			}
		};

		ServiceReference<?> serviceReference = Activator.getContext()
				.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
		IProvisioningAgentProvider agentFactory = (IProvisioningAgentProvider) Activator.getContext()
				.getService(serviceReference);
		IProvisioningAgent ourAgent;
		try {
			ourAgent = agentFactory.createAgent(null); // targetDirectory.getChild("p2agent").toURI());
		} catch (ProvisionException e) {
			throw e;
		} finally {
			Activator.getContext().ungetService(serviceReference);
		}

		MyTransport transport = new MyTransport();
		ourAgent.registerService(Transport.SERVICE_NAME, transport);

		MirrorApplication ma = new MirrorApplication() {
			{
				this.agent = ourAgent;
			}
		};
		SlicingOptions slicingOptions = new SlicingOptions();
		Map<String, String> filter = new HashMap<String, String>() {
			@Override
			public String get(Object key) {
				System.err.println("GET " + key);
				// TODO Auto-generated method stub
				return super.get(key);
			}
		};
		filter.put("osgi.os", "win32");
		filter.put("osgi.ws", "win32");
		filter.put("osgi.arch", "x86");
		filter.put(IProfile.PROP_INSTALL_FEATURES, "true");

		slicingOptions.setFilter(filter);
		slicingOptions.considerStrictDependencyOnly(false);
		ma.setSlicingOptions(slicingOptions);

		ma.setIncludePacked(false);

		RepositoryDescriptor rd = new RepositoryDescriptor();
		URI uri = URI.create("http://download.eclipse.org/releases/neon/201612211000");
		rd.setLocation(uri);
		rd.setFormat(uri);

		ma.addSource(rd);
		ma.setVerbose(false);
		ma.setLog(new IArtifactMirrorLog() {

			@Override
			public void log(IArtifactDescriptor descriptor, IStatus status) {
				System.err.println("LOG " + descriptor + " " + status);
			}

			@Override
			public void log(IStatus status) {
				System.err.println("LOG " + status);

			}

			@Override
			public void close() {
				System.err.println("CLOSE");
			}
		});

		transport.addRepositories(
				((CompositeArtifactRepository) ma.getCompositeArtifactRepository()).getLoadedChildren());
		IMetadataRepository md = ma.getCompositeMetadataRepository();

		List<IInstallableUnit> sourceIUs = new LinkedList<>();

		IQueryResult<IInstallableUnit> query = md.query(QueryUtil.ALL_UNITS, monitor);
		for (Iterator<IInstallableUnit> i = query.iterator(); i.hasNext();) {
			IInstallableUnit iu = i.next();
			if (iu.getId().equals("org.eclipse.core.runtime.feature.feature.group"))
				sourceIUs.add(iu);
		}
		ma.setSourceIUs(sourceIUs);

		Path path = Paths.get("c:/temp/mirror1");

		PermissiveSlicer slicer = new PermissiveSlicer(md, slicingOptions.getFilter(),
				slicingOptions.includeOptionalDependencies(), slicingOptions.isEverythingGreedy(),
				slicingOptions.forceFilterTo(), slicingOptions.considerStrictDependencyOnly(),
				slicingOptions.followOnlyFilteredRequirements());
		IQueryable<IInstallableUnit> slice = slicer.slice(sourceIUs.toArray(new IInstallableUnit[sourceIUs.size()]),
				monitor);

		IQueryResult<IInstallableUnit> r1 = slice.query(QueryUtil.ALL_UNITS, monitor);
		ArrayList<IArtifactKey> keys = new ArrayList<IArtifactKey>();
		for (IInstallableUnit iu : r1) {
			System.err.println("SLICE " + iu + " -- " + getType(iu));
			keys.addAll(iu.getArtifacts());

			switch (getType(iu)) {
			case source_bundle:
				break;
			case bundle:
				for (IInstallableUnit iu1 : md.query(QueryUtil.createIUQuery(iu.getId() + ".source", iu.getVersion()),
						null)) {
					System.err.println("--> SLICE " + iu1 + " " + getType(iu1));
				}
				break;
			case feature:
				// if (iu.getId().endsWith(featureSuffix)) {
				String x = stripSuffix(iu.getId(), ".feature.jar");
				System.err.println("X " + x);
				if (x.endsWith(".feature")) {
					String x1 = stripSuffix(x, ".feature");
					for (IInstallableUnit iu1 : md.query(
							QueryUtil.createIUQuery(x1 + ".source.feature" + featureSuffix, iu.getVersion()), null)) {
						System.err.println("--> SLICEF1 " + iu1 + " " + getType(iu1));
					}
				}
				for (IInstallableUnit iu1 : md
						.query(QueryUtil.createIUQuery(x + ".source" + featureSuffix, iu.getVersion()), null)) {
					System.err.println("--> SLICEF2 " + iu1 + " " + getType(iu1));
				}
				// }
				break;
			case other:
				break;
			}

		}

		IArtifactRepository destination = createDestinationArtifactRepository(
				(IArtifactRepositoryManager) ourAgent.getService(IArtifactRepositoryManager.SERVICE_NAME), path, "");
		Mirroring mirroring = new Mirroring(ma.getCompositeArtifactRepository(), destination, true);
		mirroring.setTransport(transport);
		mirroring.setIncludePacked(false);
		mirroring.setArtifactKeys(keys.toArray(new IArtifactKey[keys.size()]));

		MultiStatus multiStatus = mirroring.run(true, false);
		System.err.println("STATUS=" + multiStatus);

		if (true)
			return null;

		RepositoryDescriptor dest = new RepositoryDescriptor();
		uri = Paths.get("c:/temp/mirror1").toUri();
		dest.setLocation(uri);
		dest.setFormat(uri);

		ma.addDestination(dest);
		ma.run(monitor);

		return null;
	}

	private String stripSuffix(String id, String suffix) {
		Objects.requireNonNull(id);
		Objects.requireNonNull(suffix);
		if (!id.endsWith(suffix))
			throw new IllegalArgumentException("'" + id + "' does not end with '" + suffix + "'");
		return id.substring(0, id.length() - suffix.length());
	}

	static enum Type {
		feature, bundle, source_bundle, other
	}

	private Type getType(IInstallableUnit iu) {

		Optional<IProvidedCapability> cap = iu.getProvidedCapabilities().stream()
				.filter(p -> p.getNamespace().equals(PublisherHelper.NAMESPACE_ECLIPSE_TYPE)).findAny();

		if (!cap.isPresent())
			return Type.other;
		switch (cap.get().getName()) {
		case "source":
			return Type.source_bundle;
		case "bundle":
			return Type.bundle;
		case "feature":
			return Type.feature;
		default:
			System.err.println("UNHANDLED " + cap.get());
			return Type.other;
		}

	}

	final String featureSuffix = ".feature.group";

	private Collection<String> guessSourceUnitIds(IInstallableUnit iu) {
		System.err.println("IU " + iu.getProperties());
		String id = iu.getId();
		if (id.endsWith(".feature" + featureSuffix)) {
			id = id.substring(0, 22);
			return Arrays.asList(id + ".source.feature" + featureSuffix);
		} else if (id.endsWith(featureSuffix)) {
			id = id.substring(0, 14);
			return Arrays.asList(id + ".source.feature" + featureSuffix, id + ".feature.source" + featureSuffix);
		} else {
			return Collections.singleton(id + ".source");
		}
	}

	private IArtifactRepository createDestinationArtifactRepository(IArtifactRepositoryManager mgr, Path path,
			String string) {
		try {
			try {
				return mgr.loadRepository(path.toUri(), IArtifactRepositoryManager.REPOSITORY_HINT_MODIFIABLE,
						new NullProgressMonitor());
			} catch (ProvisionException e) {
				// assume that repository does not exist
			}

			IArtifactRepository dest;
			try {
				dest = mgr.createRepository(path.toUri(), string, IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY,
						null);
				dest.setProperty(IRepository.PROP_COMPRESSED, "true"); //$NON-NLS-1$
				return dest;
			} catch (ProvisionException e) {
				throw new RuntimeException(e);
			}
		} catch (OperationCanceledException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void stop() {
		System.err.println("Application1.stop");
	}

}
