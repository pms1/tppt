package com.github.pms1.tppt.mirror;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.artifact.processors.md5.Messages;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.director.PermissiveSlicer;
import org.eclipse.equinox.internal.p2.director.Slicer;
import org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository;
import org.eclipse.equinox.internal.p2.repository.Transport;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.engine.IProvisioningPlan;
import org.eclipse.equinox.p2.engine.ProvisioningContext;
import org.eclipse.equinox.p2.internal.repository.mirroring.Mirroring;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.planner.IPlanner;
import org.eclipse.equinox.p2.planner.IProfileChangeRequest;
import org.eclipse.equinox.p2.query.IQuery;
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

import com.github.pms1.tppt.mirror.MirrorSpec.AuthenticatedUri;
import com.github.pms1.tppt.mirror.MirrorSpec.SourceRepository;
import com.github.pms1.tppt.mirror.MyTransport.ServerParameters;

@SuppressWarnings("restriction")
public class MirrorApplication implements IApplication {

	private static boolean debug = Platform.inDebugMode();

	@SuppressWarnings("unchecked")
	private static final Map<String, String>[] emptyFilters = new Map[] { Collections.emptyMap() };

	static final List<GlobalOptions> allGo;
	static final List<SlicerOptions> allSo;
	static final List<PermissiveSlicerOptions> allPo;
	static {
		GlobalOptions go1 = new GlobalOptions();
		go1.installFeatures = true;
		GlobalOptions go2 = new GlobalOptions();
		go2.installFeatures = false;
		allGo = Arrays.asList(go1, go2);

		SlicerOptions so1 = new SlicerOptions();
		allSo = Arrays.asList(so1);

		PermissiveSlicerOptions po1 = new PermissiveSlicerOptions();
		po1.everythingGreedy = false;

		PermissiveSlicerOptions po2 = new PermissiveSlicerOptions();
		po2.everythingGreedy = true;
		allPo = Arrays.asList(po1, po2);
	}

	@SuppressWarnings({ "deprecation", "removal" })
	@Override
	public Object start(IApplicationContext context) throws IOException, NoSuchAlgorithmException {
		Object args = context.getArguments().get(IApplicationContext.APPLICATION_ARGS);

		boolean verboseApacheLog = false;
		if (verboseApacheLog) {
			Logger l = Logger.getLogger("org.apache");
			l.setLevel(Level.FINEST);
			Logger.getLogger("").getHandlers()[0].setLevel(Level.FINEST);
		}

		if (debug)
			System.out.println("MirrorApplication.commandLine                    = " + Arrays.asList((String[]) args));

		try {
			for (String s : Arrays.asList((String[]) args)) {
				MirrorSpec ms;
				if (s.equals("-")) {
					throw new UnsupportedOperationException();
				} else {
					try (InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(s));
							ObjectInputStream ois = new ObjectInputStream(is)) {
						ms = (MirrorSpec) ois.readObject();
					} catch (ClassNotFoundException e) {
						throw new RuntimeException(e);
					}
				}

				if (debug) {
					System.out.println("MirrorApplication.mirrorRepository               = " + ms.mirrorRepository);

					int idx = 0;
					for (SourceRepository repo : ms.sourceRepositories) {
						System.out.println(
								"MirrorApplication.sourceRepository[" + idx + "].uri            = " + repo.uri);
						System.out.println("MirrorApplication.sourceRepository[" + idx + "].updatePolicy   = "
								+ repo.updatePolicy);
						++idx;
					}

					if (ms.servers != null) {
						idx = 0;
						for (AuthenticatedUri u : ms.servers) {
							System.out.println("MirrorApplication.server[" + idx + "].uri                  = " + u.uri);
							System.out.println(
									"MirrorApplication.server[" + idx + "].username             = " + u.username);
							System.out.println(
									"MirrorApplication.server[" + idx + "].password             = " + u.password);
							++idx;
						}
					}
					System.out.println("MirrorApplication.targetRepository               = " + ms.targetRepository);
					System.out.println("MirrorApplication.installableUnit                = " + Arrays.toString(ms.ius));
					System.out.println("MirrorApplication.offline                        = " + ms.offline);
					System.out.println("MirrorApplication.stats                          = " + ms.stats);
					System.out.println(
							"MirrorApplication.filter                         = " + Arrays.toString(ms.filters));
					if (ms.mirrors != null) {
						idx = 0;
						for (Entry<URI, AuthenticatedUri> e : ms.mirrors.entrySet()) {
							System.out.println(
									"MirrorApplication.mirror[" + idx + "].from                 = " + e.getKey());
							System.out.println(
									"MirrorApplication.mirror[" + idx + "].to                   = " + e.getValue().uri);
							System.out.println("MirrorApplication.mirror[" + idx + "].to.username          = "
									+ e.getValue().username);
							System.out.println("MirrorApplication.mirror[" + idx + "].to.password          = "
									+ e.getValue().password);

							idx++;
						}
					}
					if (ms.proxy != null) {
						System.out.println("MirrorApplication.proxy.protocol                 = " + ms.proxy.protocol);
						System.out.println("MirrorApplication.proxy.host                     = " + ms.proxy.host);
						System.out.println("MirrorApplication.proxy.port                     = " + ms.proxy.port);
						System.out.println("MirrorApplication.proxy.username                 = " + ms.proxy.username);
						System.out.println("MirrorApplication.proxy.password                 = " + ms.proxy.password);
						System.out.println(
								"MirrorApplication.proxy.nonProxyHosts            = " + ms.proxy.nonProxyHosts);
					}
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

				IProvisioningAgent ourAgent;
				ourAgent = getAgent();

				TreeMap<URI, ServerParameters> serverParameters = new TreeMap<>();
				for (SourceRepository repo : ms.sourceRepositories) {
					UpdatePolicy updatePolicy;
					if (repo.updatePolicy == null) {
						updatePolicy = UpdatePolicy.NEVER;
					} else if (repo.updatePolicy.equals("never")) {
						updatePolicy = UpdatePolicy.NEVER;
					} else if (repo.updatePolicy.equals("always")) {
						updatePolicy = UpdatePolicy.ALWAYS;
					} else {
						throw new RuntimeException("Invalid updatePolicy: " + repo.updatePolicy);
					}

					ServerParameters parameters = new ServerParameters(updatePolicy);
					serverParameters.put(repo.uri, parameters);
				}

				MyTransport transport = new MyTransport(Path.of(ms.mirrorRepository), ms.offline, ms.stats, ms.servers,
						ms.mirrors, ms.proxy, serverParameters);
				ourAgent.registerService(Transport.SERVICE_NAME, transport);

				CompositeArtifactRepository sourceArtifactRepo = CompositeArtifactRepository
						.createMemoryComposite(ourAgent);
				if (ms.sourceRepositories == null)
					throw new IllegalArgumentException("No <sourceRepository> defined");

				for (SourceRepository sr : ms.sourceRepositories)
					sourceArtifactRepo.addChild(sr.uri);
				transport.addRepositories(sourceArtifactRepo.getLoadedChildren());

				CompositeMetadataRepository sourceMetadataRepo = CompositeMetadataRepository
						.createMemoryComposite(ourAgent);
				for (SourceRepository sr : ms.sourceRepositories)
					sourceMetadataRepo.addChild(sr.uri);

				Set<IInstallableUnit> root = new HashSet<>();
				for (String iu : ms.ius) {
					int idx = iu.indexOf('/');
					IQuery<IInstallableUnit> q;
					if (idx == -1) {
						String unit = iu;
						q = QueryUtil.createLatestQuery(QueryUtil.createIUQuery(unit));
					} else {
						String unit = iu.substring(0, idx);
						String version = iu.substring(idx + 1);
						q = QueryUtil.createLatestQuery(QueryUtil.createIUQuery(unit, Version.create(version)));
					}

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
				excludeFilter = Collections.unmodifiableList(excludeFilter);

				Set<IInstallableUnit> finalIus = new HashSet<>();

				for (Map<String, String> filter : ms.filters != null ? ms.filters : emptyFilters) {

					GlobalOptions go = new GlobalOptions();
					go.installFeatures = true;

					SlicingAlgorithm slicer;
					switch (ms.algorithm) {
					case slicer:
						SlicerOptions so = new SlicerOptions();
						slicer = createSlicer(sourceMetadataRepo, go, so, filter);
						break;
					case permissiveSlicer:
						PermissiveSlicerOptions po = new PermissiveSlicerOptions();
						po.everythingGreedy = true;
						slicer = createPermissiveSlicer(sourceMetadataRepo, go, po, filter);
						break;
					case planner:
						slicer = createPlanner(sourceMetadataRepo, go, filter, ourAgent);
						break;
					default:
						throw new Error();
					}

					Set<IInstallableUnit> root1 = new HashSet<>(root);

					for (;;) {
						int oldRootSize = root1.size();

						LinkedList<IInstallableUnit> todo = new LinkedList<>();
						for (IInstallableUnit iu : slicer.apply(root1, monitor)) {
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
								for (IInstallableUnit iu1 : sourceMetadataRepo.query(
										QueryUtil.createIUQuery(iu.getId() + ".source", iu.getVersion()), monitor))
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
										if (root1.add(iu1))
											System.out.println("Adding " + iu1 + " as source of " + iu);
								break;
							default:
								break;
							}
						}

						if (oldRootSize == root1.size())
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
				mirroring.setArtifactKeys(
						finalIus.stream().flatMap(p -> p.getArtifacts().stream()).toArray(IArtifactKey[]::new));

				for (;;) {
					MultiStatus multiStatus = mirroring.run(true, false);
					if (!multiStatus.isOK()) {
						if (handleChecksumFailure(multiStatus, transport))
							continue;

						throw new StatusException("Mirroring failed", multiStatus);
					} else {
						break;
					}
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
						throw new Error("Artifacts for " + a + ": " + Arrays.asList(ad));

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
					d.setProcessingSteps(Arrays.stream(ad[0].getProcessingSteps()).filter(
							e -> !e.getProcessorId().equals("org.eclipse.equinox.p2.processing.Pack200Unpacker"))
							.toArray(IProcessingStepDescriptor[]::new));

					try (java.io.OutputStream os = destinationArtifactRepo.getOutputStream(d)) {
						IStatus status = sourceArtifactRepo.getArtifact(ad[0], os, new NullProgressMonitor());
						if (!status.isOK())
							throw new StatusException("Retrieving artifact failed (" + ad[0] + ")", status);
					} catch (ProvisionException e) {
						throw new StatusException("Writing artifact descriptor failed", e.getStatus());
					}

				}
			}
		} catch (StatusException e) {
			System.err.println("[FATAL] " + e.getMessage());
			print(e.status, " ");
			return 1;
		}
		return null;
	}

	@SuppressWarnings("serial")
	private static class StatusException extends RuntimeException {
		private final IStatus status;

		StatusException(String message, IStatus status) {
			super(message);
			this.status = status;
		}

	}

	private void addExpected(Map<String, Set<String>> expected, String key, String value) {
		Set<String> set = expected.get(key);
		if (set == null) {
			set = new HashSet<>();
			expected.put(key, set);
		}
		set.add(value);
	}

	private void extractExpectedMD5(IStatus status, Map<String, Set<String>> expected) {
		if (status.getCode() == ProvisionException.ARTIFACT_MD5_NOT_MATCH) {
			Pattern p = Pattern.compile(Messages.Error_unexpected_hash.replaceAll("[{][01][}]", "(.*)"));
			Matcher m = p.matcher(status.getMessage());
			if (m.matches()) {
				addExpected(expected, "MD5", m.group(2));
			} else {
				p = Pattern.compile(
						org.eclipse.equinox.internal.p2.artifact.processors.checksum.Messages.Error_unexpected_checksum
								.replaceAll("[{][012][}]", "(.*)"));
				m = p.matcher(status.getMessage());
				if (m.matches()) {
					addExpected(expected, m.group(1), m.group(3));
				} else {
					throw new Error("Failed to parse message about unexpected hash: >" + status.getMessage() + "<");
				}
			}
		}

		for (IStatus c : status.getChildren()) {
			extractExpectedMD5(c, expected);
		}
	}

	private boolean handleChecksumFailure(IStatus status, MyTransport transport)
			throws IOException, NoSuchAlgorithmException {
		if (transport.getLast() == null)
			return false;

		Map<String, Set<String>> allExpected = new HashMap<>();
		extractExpectedMD5(status, allExpected);

		String expectedAlgo = null;
		String expectedSum = null;

		switch (allExpected.size()) {
		case 0:
			return false;
		case 1:
			Entry<String, Set<String>> e = allExpected.entrySet().iterator().next();
			if (e.getValue().size() == 1) {
				expectedAlgo = e.getKey();
				expectedSum = e.getValue().iterator().next();
			}
			break;
		}
		if (expectedAlgo == null || expectedSum == null)
			throw new Error("Multiple different checksum failures: " + allExpected);

		String is = MD5(expectedAlgo, transport.getLast());

		if (!expectedSum.equalsIgnoreCase(is))
			return false;

		System.err.println("[INFO] Checksum of '" + transport.getLast()
				+ "' is unexpected. Assuming file is corrupted. Deleting it and trying again.");
		Files.delete(transport.getLast());

		return true;
	}

	public String MD5(String algo, Path p) throws IOException, NoSuchAlgorithmException {
		java.security.MessageDigest md5 = java.security.MessageDigest.getInstance(algo);
		try (InputStream is = Files.newInputStream(p)) {
			byte[] buf = new byte[8192];
			int read;
			while ((read = is.read(buf)) != -1)
				md5.update(buf, 0, read);
		}
		byte[] array = md5.digest();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < array.length; ++i) {
			sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
		}
		return sb.toString();
	}

	private SlicingAlgorithm createPlanner(CompositeMetadataRepository sourceMetadataRepo, GlobalOptions go,
			Map<String, String> basicFilter, IProvisioningAgent agent) {

		IProfileRegistry registry = (IProfileRegistry) agent.getService(IProfileRegistry.SERVICE_NAME);

		return (root, monitor) -> {

			Set<IInstallableUnit> fromSlice = new HashSet<>();

			Map<String, String> filter = new HashMap<String, String>(basicFilter);
			addGlobalOptions(filter, go);

			String profileId = "MirrorApplication-" + System.currentTimeMillis();
			IProfile profile;
			try {
				profile = registry.addProfile(profileId, filter);
			} catch (ProvisionException e) {
				throw new StatusException("Adding a profile failed (" + profileId + " " + filter + ")", e.getStatus());
			}

			try {
				IPlanner planner = (IPlanner) agent.getService(IPlanner.SERVICE_NAME);
				if (planner == null)
					throw new IllegalStateException();
				IProfileChangeRequest pcr = planner.createChangeRequest(profile);
				pcr.addAll(root);

				ProvisioningContext context2 = new ProvisioningContext(agent);
				context2.setMetadataRepositories(sourceMetadataRepo.getChildren().toArray(new URI[0]));

				IProvisioningPlan plan = planner.getProvisioningPlan(pcr, context2, monitor);
				if (plan == null)
					throw new RuntimeException("Planner returned null");
				handleStatus("Planner", plan.getStatus());

				for (IInstallableUnit iu : plan.getAdditions().query(QueryUtil.ALL_UNITS, monitor))
					fromSlice.add(iu);
				if (plan.getInstallerPlan() != null)
					for (IInstallableUnit iu : plan.getInstallerPlan().getAdditions().query(QueryUtil.ALL_UNITS,
							monitor))
						fromSlice.add(iu);

			} finally {
				registry.removeProfile(profileId);
			}

			return fromSlice;
		};
	}

	static class GlobalOptions {
		Boolean installFeatures;
	}

	static class SlicerOptions {
		final boolean considerMetaRequirements = false;
	}

	static class PermissiveSlicerOptions {
		final boolean includeOptionalDependencies = true;
		Boolean everythingGreedy;
		final boolean evalFilterTo = true;
		final boolean considerOnlyStrictDependency = false;
		final boolean onlyFilteredRequirements = false;
	}

	private SlicingAlgorithm createSlicer(CompositeMetadataRepository repo, GlobalOptions go, SlicerOptions so,
			Map<String, String> basicFilter) {
		return (root, monitor) -> {
			Set<IInstallableUnit> fromSlice = new HashSet<>();

			Map<String, String> filter = new HashMap<String, String>(basicFilter);
			addGlobalOptions(filter, go);

			Slicer slicer = new Slicer(repo, filter, so.considerMetaRequirements);
			IQueryable<IInstallableUnit> slice = slicer.slice(root, monitor);
			handleStatus("Slicer", slicer.getStatus());
			if (slice == null)
				throw new StatusException("Slicer returned null", slicer.getStatus());

			// if (slice != null && slicingOptions.latestVersionOnly()) {
			// IQueryResult<IInstallableUnit> queryResult =
			// slice.query(QueryUtil.createLatestIUQuery(), monitor);
			// slice = queryResult;
			// }

			for (IInstallableUnit iu : slice.query(QueryUtil.ALL_UNITS, monitor))
				fromSlice.add(iu);

			return fromSlice;
		};
	}

	private void setOption(Map<String, String> filter, String option, String value) {
		if (value == null) {
			if (filter.containsKey(option))
				throw new Error();
		} else {
			String old = filter.putIfAbsent(option, value);
			if (old != null)
				throw new Error();
		}
	}

	private void addGlobalOptions(Map<String, String> filter, GlobalOptions go) {
		if (go.installFeatures)
			setOption(filter, IProfile.PROP_INSTALL_FEATURES, "true");
		else
			setOption(filter, IProfile.PROP_INSTALL_FEATURES, null);
	}

	private SlicingAlgorithm createPermissiveSlicer(CompositeMetadataRepository repo, GlobalOptions go,
			PermissiveSlicerOptions po, Map<String, String> basicFilter) {

		return (root, monitor) ->

		{
			Set<IInstallableUnit> fromSlice = new HashSet<>();

			Map<String, String> filter = new HashMap<String, String>(basicFilter);
			addGlobalOptions(filter, go);

			PermissiveSlicer slicer = new PermissiveSlicer(repo, filter, po.includeOptionalDependencies,
					po.everythingGreedy, po.evalFilterTo, po.considerOnlyStrictDependency, po.onlyFilteredRequirements);

			IQueryable<IInstallableUnit> slice = slicer.slice(root, monitor);
			handleStatus("PermissiveSlicer", slicer.getStatus());
			if (slice == null)
				throw new StatusException("PermissiveSlicer returned null", slicer.getStatus());

			// if (slice != null && slicingOptions.latestVersionOnly()) {
			// IQueryResult<IInstallableUnit> queryResult =
			// slice.query(QueryUtil.createLatestIUQuery(), monitor);
			// slice = queryResult;
			// }

			for (IInstallableUnit iu : slice.query(QueryUtil.ALL_UNITS, monitor))
				fromSlice.add(iu);

			return fromSlice;
		};
	}

	static void handleStatus(String prefix, IStatus status) {
		if (status.isOK())
			return;

		if (status.matches(IStatus.ERROR | IStatus.CANCEL))
			throw new StatusException(prefix + " failed", status);

		System.err.println("[INFO] " + prefix + " returned status is not OK");
		print(status, " ");
	}

	Map<String, String> why;

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

	static IProvisioningAgent getAgent() {
		ServiceReference<?> serviceReference = Activator.getContext()
				.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
		IProvisioningAgentProvider agentFactory = (IProvisioningAgentProvider) Activator.getContext()
				.getService(serviceReference);
		IProvisioningAgent ourAgent;
		try {
			ourAgent = agentFactory.createAgent(null); // targetDirectory.getChild("p2agent").toURI());
		} catch (ProvisionException e) {
			throw new StatusException("Creating an agent failed", e.getStatus());
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
			System.err.println("[WARNING] UNHANDLED " + cap.get());
			return Type.other;
		}

	}

	private final static String featureSuffix = ".feature.group";

	private IArtifactRepository createDestinationArtifactRepository(IArtifactRepositoryManager mgr, URI path,
			String name) {
		try {
			return mgr.loadRepository(path, IArtifactRepositoryManager.REPOSITORY_HINT_MODIFIABLE,
					new NullProgressMonitor());
		} catch (ProvisionException e) {
			if (e.getStatus().getCode() != ProvisionException.REPOSITORY_NOT_FOUND)
				throw new StatusException("Loading repository failed (" + path + ")", e.getStatus());
		}

		IArtifactRepository dest;
		try {
			dest = mgr.createRepository(path, name, IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY, null);
			return dest;
		} catch (ProvisionException e) {
			throw new StatusException("Creating artifact repository failed (" + path + " " + name + ")", e.getStatus());
		}
	}

	private IMetadataRepository createDestinationMetadataRepository(IMetadataRepositoryManager mgr, URI path,
			String name) {

		try {
			return mgr.loadRepository(path, IMetadataRepositoryManager.REPOSITORY_HINT_MODIFIABLE,
					new NullProgressMonitor());
		} catch (ProvisionException e) {
			if (e.getStatus().getCode() != ProvisionException.REPOSITORY_NOT_FOUND)
				throw new StatusException("Loading repository failed (" + path + ")", e.getStatus());
		}

		IMetadataRepository dest;
		try {
			dest = mgr.createRepository(path, name, IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, null);
			return dest;
		} catch (ProvisionException e) {
			throw new StatusException("Creating metadata repository failed (" + path + " " + name + ")", e.getStatus());
		}
	}

	@Override
	public void stop() {
	}

}
