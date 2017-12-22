package com.github.pms1.tppt.mirror;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.bind.JAXB;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.artifact.processors.md5.Messages;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.director.PermissiveSlicer;
import org.eclipse.equinox.internal.p2.director.Slicer;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
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
import org.eclipse.equinox.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.IInstallableUnitPatch;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.IRequirementChange;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;
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

@SuppressWarnings("restriction")
public class MirrorApplication implements IApplication {

	private static boolean debug = false;

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
			excludeFilter = Collections.unmodifiableList(excludeFilter);

			if (false) {

				for (GlobalOptions go : allGo) {
					for (SlicerOptions so : allSo) {

						SlicingAlgorithm slicer = createSlicer(sourceMetadataRepo, go, so, ms.filters[0]);
						SlicingAlgorithm my = myMirror(sourceMetadataRepo, go, myOptions(so), ms.filters[0]);

						if (diff("MS", my.apply(root, monitor), slicer.apply(root, monitor))) {
							// throw new Error();
						}
					}
				}

				for (GlobalOptions go : allGo) {
					for (PermissiveSlicerOptions so : allPo) {

						SlicingAlgorithm slicer = createPermissiveSlicer(sourceMetadataRepo, go, so, ms.filters[0]);
						SlicingAlgorithm my = myMirror(sourceMetadataRepo, go, myOptions(so), ms.filters[0]);

						if (diff("MP", my.apply(root, monitor), slicer.apply(root, monitor))) {
							// throw new Error();
						}
					}
				}
				for (GlobalOptions go : allGo) {
					SlicingAlgorithm planner = createPlanner(sourceMetadataRepo, go, ms.filters[0], ourAgent);
					MyMirrorOptions mo = new MyMirrorOptions();
					mo.everythingGreedy = false;
					SlicingAlgorithm my = myMirror(sourceMetadataRepo, go, mo, ms.filters[0]);
					diff("ML", my.apply(root, monitor), planner.apply(root, monitor));
				}

				for (Map.Entry<String, String> e : why.entrySet())
					System.err.println(e);

				System.exit(1);
			}

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
			mirroring.setIncludePacked(false);
			mirroring.setArtifactKeys(
					finalIus.stream().flatMap(p -> p.getArtifacts().stream()).toArray(IArtifactKey[]::new));

			for (;;) {
				MultiStatus multiStatus = mirroring.run(true, false);
				if (!multiStatus.isOK()) {
					print(multiStatus, "");

					if (handleChecksumFailure(multiStatus, transport))
						continue;

					return 1;
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

	private boolean handleChecksumFailure(MultiStatus multiStatus, MyTransport transport) throws Exception {
		if (transport.last == null)
			return false;

		if (multiStatus.getChildren().length != 1)
			return false;
		IStatus c1 = multiStatus.getChildren()[0];
		if (c1.getChildren().length != 1)
			return false;
		IStatus c2 = c1.getChildren()[0];
		if (c2.getChildren().length != 0)
			return false;
		if (c2.getCode() != ProvisionException.ARTIFACT_MD5_NOT_MATCH)
			return false;
		Pattern p = Pattern.compile(Messages.Error_unexpected_hash.replaceAll("[{][01][}]", "(.*)"));
		Matcher m = p.matcher(c2.getMessage());
		if (!m.matches())
			return false;

		String expected = m.group(2);

		String is = MD5(transport.last);

		if (!expected.equalsIgnoreCase(is))
			return false;

		System.err.println("Checksum of '" + transport.last
				+ "' is unexpected. Assuming file is corrupted. Deleting it and trying again.");
		Files.delete(transport.last);
		return true;
	}

	public String MD5(Path p) throws IOException, NoSuchAlgorithmException {
		java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
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
			try {
				Set<IInstallableUnit> fromSlice = new HashSet<>();

				Map<String, String> filter = new HashMap<String, String>(basicFilter);
				addGlobalOptions(filter, go);

				String profileId = "MirrorApplication-" + System.currentTimeMillis();
				IProfile profile = registry.addProfile(profileId, filter);
				try {
					IPlanner planner = (IPlanner) agent.getService(IPlanner.SERVICE_NAME);
					if (planner == null)
						throw new IllegalStateException();
					IProfileChangeRequest pcr = planner.createChangeRequest(profile);
					pcr.addAll(root);

					ProvisioningContext context2 = new ProvisioningContext(agent);
					context2.setMetadataRepositories(sourceMetadataRepo.getChildren().toArray(new URI[0]));

					IProvisioningPlan plan = planner.getProvisioningPlan(pcr, context2, monitor);

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
			} catch (ProvisionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			throw new Error();

			// IProfileRegistry registry = Activator.getProfileRegistry();
			// String profileId = "MirrorApplication-" + System.currentTimeMillis();
			// //$NON-NLS-1$
			// IProfile profile = registry.addProfile(profileId,
			// slicingOptions.getFilter());
			// IPlanner planner = (IPlanner)
			// Activator.getAgent().getService(IPlanner.SERVICE_NAME);
			// if (planner == null)
			// throw new IllegalStateException();
			// IProfileChangeRequest pcr = planner.createChangeRequest(profile);
			// pcr.addAll(sourceIUs);
			// IProvisioningPlan plan = planner.getProvisioningPlan(pcr, null, monitor);
			// registry.removeProfile(profileId);
			// @SuppressWarnings("unchecked")
			// IQueryable<IInstallableUnit>[] arr = new IQueryable[plan.getInstallerPlan()
			// == null ? 1 : 2];
			// arr[0] = plan.getAdditions();
			// if (plan.getInstallerPlan() != null)
			// arr[1] = plan.getInstallerPlan().getAdditions();
			// return null;
		};
	}

	private MyMirrorOptions myOptions(PermissiveSlicerOptions so) {
		if (so.considerOnlyStrictDependency)
			throw new Error();

		if (!so.evalFilterTo)
			throw new Error();

		if (!so.includeOptionalDependencies)
			throw new Error();

		if (so.onlyFilteredRequirements)
			throw new Error();

		MyMirrorOptions result = new MyMirrorOptions();
		result.everythingGreedy = so.everythingGreedy;
		return result;
	}

	private MyMirrorOptions myOptions(SlicerOptions so) {
		if (so.considerMetaRequirements)
			throw new Error();

		MyMirrorOptions result = new MyMirrorOptions();
		result.everythingGreedy = false;
		return result;
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
			IQueryable<IInstallableUnit> slice = slicer.slice(root.stream().toArray(IInstallableUnit[]::new), monitor);

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

			IQueryable<IInstallableUnit> slice = slicer.slice(root.stream().toArray(IInstallableUnit[]::new), monitor);

			if (slice == null) {
				print(slicer.getStatus(), "");
				throw new Error("slicing failed for " + root);
			}

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

	@SafeVarargs
	private final boolean diff(String ids, Set<IInstallableUnit>... data) {
		if (ids.length() != data.length)
			throw new IllegalArgumentException();

		Set<IInstallableUnit> all = new TreeSet<>((a, b) -> {
			int diff = a.getId().compareTo(b.getId());
			if (diff != 0)
				return diff;
			return a.getVersion().compareTo(b.getVersion());
		});

		for (Set<IInstallableUnit> d : data)
			all.addAll(d);

		Map<String, Integer> count = new TreeMap<>();
		for (IInstallableUnit iu : all) {
			String out = "";

			for (int i = 0; i != data.length; ++i)
				out += data[i].contains(iu) ? ids.charAt(i) : " ";

			if (out.contains(" "))
				System.out.println(out + " " + iu.getId() + " " + iu.getVersion());

			count.put(out, count.getOrDefault(out, 0) + 1);
		}

		for (Entry<String, Integer> e : count.entrySet())
			System.out.println(e.getKey() + " = " + e.getValue());

		return count.size() != 1;
	}

	MyMirrorOptions optionsSlicer(boolean considerMetaRequirements) {
		return new MyMirrorOptions();
	}

	static class MyMirrorOptions {

		public Boolean everythingGreedy;

	}

	Map<String, String> why;

	private SlicingAlgorithm myMirror(CompositeMetadataRepository sourceMetadataRepo, GlobalOptions go,
			MyMirrorOptions mo, Map<String, String> filter) {

		// filter.put("org.eclipse.epp.install.roots", "true");

		Predicate<IMatchExpression<IInstallableUnit>> filter1;

		if (filter == null) {
			filter1 = (x) -> true;
		} else {
			List<IInstallableUnit> filterUnits = new ArrayList<>();

			HashMap filter2 = new HashMap<>(filter);
			addGlobalOptions(filter2, go);

			IInstallableUnit unit = InstallableUnit.contextIU(filter2);

			filterUnits.add(unit);

			filter1 = (x) -> {
				if (x == null)
					return true;

				for (IInstallableUnit f : filterUnits)
					if (x.isMatch(f))
						return true;

				return false;
			};
		}

		return (root, monitor) -> {
			why = new TreeMap<>();

			Set<IInstallableUnit> all = new HashSet<>();
			Map<String, Collection<IInstallableUnit>> cache = new HashMap<>();
			for (IInstallableUnit a : sourceMetadataRepo.query(QueryUtil.ALL_UNITS, monitor)) {
				all.add(a);

				for (IProvidedCapability c : a.getProvidedCapabilities()) {
					String id = c.getNamespace() + "\t" + c.getName();
					cache.computeIfAbsent(id, (x) -> new HashSet<>()).add(a);
				}
			}

			Set<IInstallableUnit> in = new HashSet<>();
			Set<IInstallableUnit> done = new HashSet<>();
			Set<IInstallableUnit> todo = new HashSet<>();

			todo.addAll(root);

			while (!todo.isEmpty()) {
				Set<IInstallableUnit> oldTodo = todo;
				todo = new HashSet<>();
				Set<IInstallableUnit> oldIn = new HashSet<>(in);

				for (IInstallableUnit iu : oldTodo) {
					if (!done.add(iu))
						continue;

					if (!filter1.test(iu.getFilter()))
						continue;

					in.add(iu);

					if (!iu.getMetaRequirements().isEmpty())
						throw new Error();

					Collection<IRequirement> allRequirements = allRequirements(iu);

					if (false)
						System.err.println("IU " + iu);
					for (IRequirement req : allRequirements) {
						IRequiredCapability req2 = (IRequiredCapability) req;

						if (!mo.everythingGreedy)
							if (!req2.isGreedy())
								continue;

						if (false)
							System.err.println(
									" R " + req2 + " " + req2.getMax() + " " + req2.getMin() + " " + req2.isGreedy());

						if (!filter1.test(req2.getFilter()))
							continue;

						String id = req2.getNamespace() + "\t" + req2.getName();

						Collection<IInstallableUnit> collection = cache.getOrDefault(id, Collections.emptySet());

						collection = collection.stream().filter(

								iu2 -> req2.isMatch(iu2)

						).collect(Collectors.toSet());

						if (collection.isEmpty()) {
							if (req2.getMin() == 0)
								continue;
						}

						if (req.getMax() == 0)
							continue;

						if (false)
							if (collection.size() > 1) {
								Map<String, TreeSet<IInstallableUnit>> byIu = new HashMap<>();

								for (IInstallableUnit iu1 : collection) {
									byIu.computeIfAbsent(iu1.getId(),
											(x) -> new TreeSet<>(Comparator.comparing(IInstallableUnit::getVersion)))
											.add(iu1);
								}

								collection = byIu.values().stream().map(TreeSet::last).collect(Collectors.toSet());
							}
						if (collection.isEmpty())
							throw new Error();

						if (false) {
							if (collection.size() > 1) {
								System.err.println("  MULTI INCLUDE");
							}
							for (IInstallableUnit c : collection) {
								System.err.println("  ADD " + c);
							}
						}

						todo.addAll(collection);

						for (IInstallableUnit iu1 : collection) {
							why.putIfAbsent(iu1.getId() + " " + iu1.getVersion(), req + " of " + iu);
						}
					}
				}
			}

			return in;
		};

	}

	private Collection<IRequirement> allRequirements(IInstallableUnit iu) {
		if (!(iu instanceof IInstallableUnitPatch) && !(iu instanceof IInstallableUnitFragment))
			return iu.getRequirements();

		Set<IRequirement> allRequirements = new HashSet<>(iu.getRequirements());

		if (iu instanceof IInstallableUnitPatch)
			((IInstallableUnitPatch) iu).getRequirementsChange().stream() //
					.map(IRequirementChange::newValue) //
					.forEach(allRequirements::add);

		if (iu instanceof IInstallableUnitFragment)
			allRequirements.addAll(((IInstallableUnitFragment) iu).getHost());

		return allRequirements;
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
