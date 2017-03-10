package application;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.xml.bind.JAXB;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.director.PermissiveSlicer;
import org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository;
import org.eclipse.equinox.internal.p2.repository.Transport;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.internal.repository.mirroring.Mirroring;
import org.eclipse.equinox.p2.internal.repository.tools.SlicingOptions;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.osgi.framework.ServiceReference;

@SuppressWarnings("restriction")
public class Application1 implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		Object args = context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		System.out.println("Application1.commandLine        = " + Arrays.asList((String[]) args));

		for (String s : Arrays.asList((String[]) args)) {
			MirrorSpec ms;
			if (s.equals("-")) {
				ms = JAXB.unmarshal(System.in, MirrorSpec.class);
			} else {
				try (InputStream is = Files.newInputStream(Paths.get(s))) {
					ms = JAXB.unmarshal(is, MirrorSpec.class);
				}
			}
			System.out.println("Application1.mirrorRepository   = " + ms.mirrorRepository);
			System.out.println("Application1.sourceRepositories = " + Arrays.toString(ms.sourceRepositories));
			System.out.println("Application1.targetRepository   = " + ms.targetRepository);
			System.out.println("Application1.installableUnit    = " + Arrays.toString(ms.ius));
			System.out.println("Application1.offline            = " + ms.offline);
			System.out.println("Application1.stats              = " + ms.stats);
			System.out.println("Application1.filter             = " + Arrays.toString(ms.filters));

			IProgressMonitor monitor = new IProgressMonitor() {

				@Override
				public void worked(int work) {
					// TODO Auto-generated method stub

				}

				@Override
				public void subTask(String name) {
					// System.err.println("ST " + name);
				}

				@Override
				public void setTaskName(String name) {
					// System.err.println("STN " + name);
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
					// System.err.println("DONE");
				}

				@Override
				public void beginTask(String name, int totalWork) {
					// System.err.println("BT " + name);
				}
			};

			IProvisioningAgent ourAgent = getAgent();

			MyTransport transport = new MyTransport(ms.mirrorRepository, ms.offline, ms.stats);
			ourAgent.registerService(Transport.SERVICE_NAME, transport);

			CompositeArtifactRepository sourceArtifactRepo = CompositeArtifactRepository
					.createMemoryComposite(ourAgent);
			for (URI sr : ms.sourceRepositories)
				sourceArtifactRepo.addChild(sr);
			transport.addRepositories(sourceArtifactRepo.getLoadedChildren());

			CompositeMetadataRepository sourceMetadataRepo = CompositeMetadataRepository
					.createMemoryComposite(ourAgent);
			for (URI sr : ms.sourceRepositories)
				sourceMetadataRepo.addChild(sr);

			Set<IInstallableUnit> finalIus = new HashSet<>();

			for (Map<String, String> basicFilter : ms.filters != null ? ms.filters
					: new Map[] { Collections.emptyMap() }) {
				SlicingOptions slicingOptions = new SlicingOptions();
				Map<String, String> filter = new HashMap<String, String>(basicFilter);
				filter.put(IProfile.PROP_INSTALL_FEATURES, "true");
				slicingOptions.setFilter(filter);
				slicingOptions.considerStrictDependencyOnly(false);

				Set<IInstallableUnit> root = new HashSet<>();
				for (String iu : ms.ius) {
					// TODO: allow version to be specified, only latest version,
					// ...
					for (IInstallableUnit iu1 : sourceMetadataRepo.query(QueryUtil.createIUQuery(iu), monitor))
						root.add(iu1);
				}

				for (;;) {
					int oldRootSize = root.size();

					PermissiveSlicer slicer = new PermissiveSlicer(sourceMetadataRepo, slicingOptions.getFilter(),
							slicingOptions.includeOptionalDependencies(), slicingOptions.isEverythingGreedy(),
							slicingOptions.forceFilterTo(), slicingOptions.considerStrictDependencyOnly(),
							slicingOptions.followOnlyFilteredRequirements());

					IQueryable<IInstallableUnit> slice = slicer.slice(root.stream().toArray(IInstallableUnit[]::new),
							monitor);

					LinkedList<IInstallableUnit> todo = new LinkedList<>();
					for (IInstallableUnit iu : slice.query(QueryUtil.ALL_UNITS, monitor))
						todo.add(iu);

					while (!todo.isEmpty()) {
						IInstallableUnit iu = todo.removeFirst();
						if (!finalIus.add(iu))
							continue;

						switch (getType(iu)) {
						case bundle:
							for (IInstallableUnit iu1 : sourceMetadataRepo
									.query(QueryUtil.createIUQuery(iu.getId() + ".source", iu.getVersion()), monitor))
								if (getType(iu1) == Type.source_bundle)
									todo.add(iu1);
							break;
						case feature:
							if (!iu.getId().endsWith(".feature.jar"))
								throw new Error("");
							String shortId = removeSuffix(iu.getId(), ".feature.jar");
							List<String> cand = new ArrayList<>();
							cand.add(shortId + ".source.feature.group");
							if (shortId.endsWith(".feature")) {
								String s2 = removeSuffix(shortId, ".feature");
								cand.add(s2 + ".source.feature.group");
								cand.add(s2 + ".source.feature.feature.group");
							}
							for (String c : cand)
								for (IInstallableUnit iu1 : sourceMetadataRepo
										.query(QueryUtil.createIUQuery(c, iu.getVersion()), monitor))
									if (root.add(iu1))
										System.out.println("Adding " + iu1 + " as source of " + iu);
							break;
						default:
							break;
						}
					}

					if (oldRootSize == root.size())
						break;
				}
			}

			// mirror metadata
			IMetadataRepository destinationMetadataRepo = createDestinationMetadataRepository(
					(IMetadataRepositoryManager) ourAgent.getService(IMetadataRepositoryManager.SERVICE_NAME),
					ms.targetRepository, "");

			destinationMetadataRepo.addInstallableUnits(finalIus);

			// mirror artifacts
			IArtifactRepository destinationArtifactRepo = createDestinationArtifactRepository(
					(IArtifactRepositoryManager) ourAgent.getService(IArtifactRepositoryManager.SERVICE_NAME),
					ms.targetRepository, "");

			Mirroring mirroring = new Mirroring(sourceArtifactRepo, destinationArtifactRepo, true);
			mirroring.setTransport(transport);
			mirroring.setIncludePacked(false);
			mirroring.setArtifactKeys(
					finalIus.stream().flatMap(p -> p.getArtifacts().stream()).toArray(IArtifactKey[]::new));

			MultiStatus multiStatus = mirroring.run(true, false);
			System.err.println("STATUS=" + multiStatus);
		}

		return null;
	}

	static IProvisioningAgent getAgent() throws ProvisionException {
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
		return ourAgent;
	}

	private String removeSuffix(String id, String suffix) {
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

	private IArtifactRepository createDestinationArtifactRepository(IArtifactRepositoryManager mgr, Path path,
			String name) {
		try {
			try {
				return mgr.loadRepository(path.toUri(), IArtifactRepositoryManager.REPOSITORY_HINT_MODIFIABLE,
						new NullProgressMonitor());
			} catch (ProvisionException e) {
				// assume that repository does not exist
			}

			IArtifactRepository dest;
			try {
				dest = mgr.createRepository(path.toUri(), name, IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY,
						null);
				return dest;
			} catch (ProvisionException e) {
				throw new RuntimeException(e);
			}
		} catch (OperationCanceledException e) {
			throw new RuntimeException(e);
		}
	}

	private IMetadataRepository createDestinationMetadataRepository(IMetadataRepositoryManager mgr, Path path,
			String string) {
		try {
			try {
				return mgr.loadRepository(path.toUri(), IMetadataRepositoryManager.REPOSITORY_HINT_MODIFIABLE,
						new NullProgressMonitor());
			} catch (ProvisionException e) {
				// assume that repository does not exist
			}

			IMetadataRepository dest;
			try {
				dest = mgr.createRepository(path.toUri(), string, IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY,
						null);
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
