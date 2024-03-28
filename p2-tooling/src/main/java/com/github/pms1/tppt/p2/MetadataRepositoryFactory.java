package com.github.pms1.tppt.p2;

import javax.inject.Named;
import javax.inject.Singleton;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataProperties;
import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.github.pms1.tppt.p2.jaxb.metadata.Unit;
import com.github.pms1.tppt.p2.jaxb.metadata.Units;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

@Named("default")
@Singleton
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
				throw new RuntimeException(t);
			}
		}
	}

	public static JAXBContext getJaxbContext() {
		return Holder.context;
	}

	@Override
	protected void normalize(MetadataRepository t) {
		MetadataProperties properties = t.getProperties();
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
			u.getRequires().setSize(u.getRequires().getRequiredOrRequiredProperties().size());
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
		repository.setProperties(new MetadataProperties());
		repository.setUnits(new Units());
		return repository;
	}

}
