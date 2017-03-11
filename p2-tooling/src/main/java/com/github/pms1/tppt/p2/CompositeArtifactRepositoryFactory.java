package com.github.pms1.tppt.p2;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.codehaus.plexus.component.annotations.Component;
import org.osgi.framework.Version;

import com.github.pms1.tppt.p2.jaxb.composite.Children;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.composite.Properties;
import com.google.common.base.Throwables;

@Component(role = CompositeArtifactRepositoryFactory.class)
public class CompositeArtifactRepositoryFactory extends AbstractRepositoryFactory<CompositeRepository> {

	protected CompositeArtifactRepositoryFactory() {
		super(getJaxbContext(), CompositeRepository.class, "compositeArtifact", "compositeRepository.xsd");
	}

	private static class Holder {
		private final static JAXBContext context;
		static {
			try {
				context = JAXBContext.newInstance(CompositeRepository.class);
			} catch (JAXBException t) {
				throw Throwables.propagate(t);
			}
		}
	}

	public static JAXBContext getJaxbContext() {
		return Holder.context;
	}

	public CompositeRepository createEmpty() {
		CompositeRepository repository = new CompositeRepository();
		repository.setType("org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository");
		repository.setVersion(Version.parseVersion("1.0.0"));
		repository.setChildren(new Children());
		repository.setProperties(new Properties());
		return repository;
	}

}
