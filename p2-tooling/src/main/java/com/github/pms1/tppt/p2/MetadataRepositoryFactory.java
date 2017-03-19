package com.github.pms1.tppt.p2;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.codehaus.plexus.component.annotations.Component;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Properties;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;
import com.github.pms1.tppt.p2.jaxb.metadata.Units;
import com.google.common.base.Throwables;

@Component(role = MetadataRepositoryFactory.class)
public class MetadataRepositoryFactory extends AbstractRepositoryFactory<MetadataRepository> {

	protected MetadataRepositoryFactory() {
		super(getJaxbContext(), MetadataRepository.class, "metadata", "1.1.0", "metadataRepository.xsd");
	}

	private static class Holder {
		private final static JAXBContext context;
		static {
			try {
				context = JAXBContext.newInstance(MetadataRepository.class);
			} catch (JAXBException t) {
				throw Throwables.propagate(t);
			}
		}
	}

	public static JAXBContext getJaxbContext() {
		return Holder.context;
	}

	@Override
	protected void normalize(MetadataRepository t) {
		Properties properties = t.getProperties();
		if (properties != null)
			properties.setSize(properties.getProperty().size());

		Units units = t.getUnits();
		if (units != null) {
			units.setSize(units.getUnit().size());
			for (Unit u : units.getUnit())
				normalize(u);
		}

	}

	private void normalize(Unit u) {
		if (u.getRequires() != null)
			u.getRequires().setSize(u.getRequires().getRequired().size());
		if (u.getProperties() != null)
			u.getProperties().setSize(u.getProperties().getProperty().size());
		if (u.getProvides() != null)
			u.getProvides().setSize(u.getProvides().getProvided().size());
	}

	@Override
	protected MetadataRepository createEmpty() {
		MetadataRepository repository = new MetadataRepository();
		repository.setType("org.eclipse.equinox.internal.p2.metadata.repository.LocalMetadataRepository");
		repository.setVersion("1");
		repository.setProperties(new Properties());
		repository.setUnits(new Units());
		return repository;
	}

}
