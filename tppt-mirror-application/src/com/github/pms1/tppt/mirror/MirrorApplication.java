package com.github.pms1.tppt.mirror;

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
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.IProcessingStepDescriptor;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.osgi.framework.ServiceReference;

@SuppressWarnings("restriction")
public class MirrorApplication implements IApplication {

	private static boolean debug = false;

	@SuppressWarnings("unchecked")
	private static final Map<String, String>[] emptyFilters = new Map[] { Collections.emptyMap() };

	@Override
	public Object start(IApplicationContext context) throws Exception {
		Object args = context.getArguments().get(IApplicationContext.APPLICATION_ARGS);

		if (debug)
			System.out.println("MirrorApplication.commandLine        = " + Arrays.asList((String[]) args));

		for (String s : Arrays.asList((String[]) args)) {
			MirrorSpec ms;
			if (s.equals("-")) {
				ms = JAXB.unmarshal(System.in, MirrorSpec.class);
			} else {
				try (InputStream is = Files.newInputStream(Paths.get(s))) {
					ms = JAXB.unmarshal(is, MirrorSpec.class);
				}
			}

			if (debug) {
				System.out.println("MirrorApplication.mirrorRepository   = " + ms.mirrorRepository);
				System.out.println("MirrorApplication.sourceRepositories = " + Arrays.toString(ms.sourceRepositories));
				System.out.println("MirrorApplication.targetRepository   = " + ms.targetRepository);
				System.out.println("MirrorApplication.installableUnit    = " + Arrays.toString(ms.ius));
				System.out.println("MirrorApplication.offline            = " + ms.offline);
				System.out.println("MirrorApplication.stats              = " + ms.stats);
				System.out.println("MirrorApplication.filter             = " + Arrays.toString(ms.filters));
			}

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

			for (Map<String, String> basicFilter : ms.filters != null ? ms.filters : emptyFilters) {
				SlicingOptions slicingOptions = new SlicingOptions();
				Map<String, String> filter = new HashMap<String, String>(basicFilter);
				filter.put(IProfile.PROP_INSTALL_FEATURES, "true");
				slicingOptions.setFilter(filter);
				slicingOptions.considerStrictDependencyOnly(false);
				// true is likely to be always wrong as it removes them without
				// looking at constraints
				// e.g. antlr runtime 3.2.0 for xtext if 4.x is present
				slicingOptions.latestVersionOnly(false);

				Set<IInstallableUnit> root = new HashSet<>();
				for (String iu : ms.ius) {
					// TODO: allow version to be specified...

					IQuery<IInstallableUnit> q = QueryUtil.createLatestQuery(QueryUtil.createIUQuery(iu));

					Set<IInstallableUnit> queryResult = sourceMetadataRepo.query(q, monitor).toUnmodifiableSet();
					if (queryResult.isEmpty())
						throw new RuntimeException("IU not found: " + iu);

					root.addAll(queryResult);
				}

				List<Predicate<IInstallableUnit>> excludeFilter = new ArrayList<>();
				if (ms.excludeIus != null)
					for (String iu : ms.excludeIus) {
						int idx = iu.indexOf('/');
						Predicate<IInstallableUnit> p;
						if (idx == -1) {
							String unit = iu;
							p = iiu -> Objects.equals(unit, iiu.getId());
						} else {
							String unit = iu.substring(0, idx);
							String version = iu.substring(idx + 1);
							p = iiu -> Objects.equals(unit, iiu.getId())
									&& Objects.equals(version, iiu.getVersion().toString());
						}
						excludeFilter.add(p);
					}

				for (;;) {
					int oldRootSize = root.size();

					PermissiveSlicer slicer = new PermissiveSlicer(sourceMetadataRepo, slicingOptions.getFilter(),
							slicingOptions.includeOptionalDependencies(), slicingOptions.isEverythingGreedy(),
							slicingOptions.forceFilterTo(), slicingOptions.considerStrictDependencyOnly(),
							slicingOptions.followOnlyFilteredRequirements());

					IQueryable<IInstallableUnit> slice = slicer.slice(root.stream().toArray(IInstallableUnit[]::new),
							monitor);

					if (slice != null && slicingOptions.latestVersionOnly()) {
						IQueryResult<IInstallableUnit> queryResult = slice.query(QueryUtil.createLatestIUQuery(),
								monitor);
						slice = queryResult;
					}

					LinkedList<IInstallableUnit> todo = new LinkedList<>();
					for (IInstallableUnit iu : slice.query(QueryUtil.ALL_UNITS, monitor)) {
						if (excludeFilter.stream().anyMatch(p -> p.test(iu)))
							continue;
						todo.add(iu);
					}

					while (!todo.isEmpty()) {
						IInstallableUnit iu = todo.removeFirst();
						if (!finalIus.add(iu))
							continue;

						switch (getType(iu)) {
						case bundle:
							for (IInstallableUnit iu1 : sourceMetadataRepo
									.query(QueryUtil.createIUQuery(iu.getId() + ".source", iu.getVersion()), monitor))
								if (getType(iu1) == Type.source_bundle) {
									if (!finalIus.contains(iu1) && todo.add(iu1))
										System.out.println("Adding " + iu1 + " as source of " + iu);
								}
							break;
						case feature:
							if (!iu.getId().endsWith(".feature.jar"))
								throw new Error("");
							String shortId = removeSuffix(iu.getId(), ".feature.jar");
							List<String> cand = new ArrayList<>();
							cand.add(shortId + ".source" + featureSuffix);
							if (shortId.endsWith(".feature")) {
								String s2 = removeSuffix(shortId, ".feature");
								cand.add(s2 + ".source" + featureSuffix);
								cand.add(s2 + ".source.feature" + featureSuffix);
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

			// mirror all artifacts that have a non-packed descriptor
			Mirroring mirroring = new Mirroring(sourceArtifactRepo, destinationArtifactRepo, true);
			mirroring.setTransport(transport);
			mirroring.setIncludePacked(false);
			mirroring.setArtifactKeys(
					finalIus.stream().flatMap(p -> p.getArtifacts().stream()).toArray(IArtifactKey[]::new));

			MultiStatus multiStatus = mirroring.run(true, false);
			if (!multiStatus.isOK()) {
				print(multiStatus, "");
				return 1;
			}

			// additionally mirror packed-only artifacts, unpacking them in the
			// process
			for (IArtifactKey a : finalIus.stream().flatMap(p -> p.getArtifacts().stream())
					.toArray(IArtifactKey[]::new)) {

				IArtifactDescriptor[] ad = sourceArtifactRepo.getArtifactDescriptors(a);
				if (Arrays.stream(ad).anyMatch(
						d -> !IArtifactDescriptor.FORMAT_PACKED.equals(d.getProperty(IArtifactDescriptor.FORMAT))))
					continue;

				if (ad.length != 1)
					throw new Error();

				ArtifactDescriptor d = new ArtifactDescriptor(a);

				for (Map.Entry<String, String> e : ad[0].getProperties().entrySet()) {
					switch (e.getKey()) {
					case IArtifactDescriptor.FORMAT:
					case IArtifactDescriptor.DOWNLOAD_MD5:
					case IArtifactDescriptor.DOWNLOAD_SIZE:
						break;
					case IArtifactDescriptor.ARTIFACT_MD5:
					case IArtifactDescriptor.ARTIFACT_SIZE:
						// the unpacked jar does not necessarily match those,
						// so we remove them
						break;
					default:
						// this could be other checksums that potentially are
						// wrong after unpacking. But it could also
						// be use full metadata like maven coordinates. For the
						// moment, we keep those properties
						// to avoid loosing useful data
						d.setProperty(e.getKey(), e.getValue());
						break;
					}
				}

				// copy all processing steps but the unpacking
				d.setProcessingSteps(Arrays.stream(ad[0].getProcessingSteps())
						.filter(e -> !e.getProcessorId().equals("org.eclipse.equinox.p2.processing.Pack200Unpacker"))
						.toArray(IProcessingStepDescriptor[]::new));

				try (java.io.OutputStream os = destinationArtifactRepo.getOutputStream(d)) {
					IStatus status = sourceArtifactRepo.getArtifact(ad[0], os, new NullProgressMonitor());
					if (!status.isOK()) {
						print(status, "");
						return 1;
					}
				}

			}
		}

		return null;

	}

	static void print(IStatus s, String indent) {
		List<String> severities = new LinkedList<>();
		int sev = s.getSeverity();
		if ((sev & IStatus.OK) != 0) {
			severities.add("OK");
			sev &= ~IStatus.OK;
		}
		if ((sev & IStatus.ERROR) != 0) {
			severities.add("ERROR");
			sev &= ~IStatus.ERROR;
		}
		if ((sev & IStatus.WARNING) != 0) {
			severities.add("WARNING");
			sev &= ~IStatus.WARNING;
		}
		if ((sev & IStatus.INFO) != 0) {
			severities.add("INFO");
			sev &= ~IStatus.INFO;
		}
		if ((sev & IStatus.CANCEL) != 0) {
			severities.add("CANCEL");
			sev &= ~IStatus.CANCEL;
		}
		if (sev != 0)
			severities.add(String.valueOf(sev));

		System.err.print(indent + "[" + severities.stream().collect(Collectors.joining(",")) + "] " + s.getPlugin()
				+ " " + s.getCode() + " " + s.getMessage());
		if (s.getException() != null)
			System.err.print(" " + s.getException());
		System.err.println();
		for (IStatus c : s.getChildren())
			print(c, indent + "  ");
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

	private final static String featureSuffix = ".feature.group";

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
	}

}
