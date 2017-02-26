package application;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.repository.Transport;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.internal.repository.mirroring.IArtifactMirrorLog;
import org.eclipse.equinox.p2.internal.repository.tools.MirrorApplication;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;
import org.eclipse.equinox.p2.internal.repository.tools.SlicingOptions;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.osgi.framework.ServiceReference;

public class Application1 implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		System.err.println("Application1.start " + context);
		Object args = context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		System.err.println("Application1.start " + Arrays.asList((String[]) args));
		Object x = MirrorApplication.class;
		System.err.println("Application1.start " + x);

		System.err.println(System.out);
		System.err.println(System.err);
		if (true) {

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
			monitor = null;

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
			SlicingOptions so = new SlicingOptions();
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

			so.setFilter(filter);
			so.considerStrictDependencyOnly(false);
			ma.setSlicingOptions(so);

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
			List<IInstallableUnit> ius = new LinkedList<>();

			IQueryResult<IInstallableUnit> query = md.query(QueryUtil.ALL_UNITS, monitor);
			for (Iterator<IInstallableUnit> i = query.iterator(); i.hasNext();) {
				IInstallableUnit iu = i.next();
				if (iu.getId().equals("org.eclipse.core.runtime.feature.feature.group"))
					ius.add(iu);
			}
			ma.setSourceIUs(ius);

			RepositoryDescriptor dest = new RepositoryDescriptor();
			uri = Paths.get("c:/temp/mirror1").toUri();
			dest.setLocation(uri);
			dest.setFormat(uri);

			ma.addDestination(dest);
			ma.run(monitor);

		}

		return null;
	}

	@Override
	public void stop() {
		System.err.println("Application1.stop");
	}

}
