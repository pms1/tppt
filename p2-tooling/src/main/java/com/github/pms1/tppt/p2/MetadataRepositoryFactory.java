package com.github.pms1.tppt.p2;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.codehaus.plexus.component.annotations.Component;

import com.github.pms1.tppt.p2.jaxb.metadata.MetadataRepository;
import com.google.common.base.Throwables;

@Component(role = MetadataRepositoryFactory.class)
public class MetadataRepositoryFactory extends AbstractRepositoryFactory<MetadataRepository> {

	protected MetadataRepositoryFactory() {
		super(getJaxbContext(), MetadataRepository.class, "metadata", "metadataRepository.xsd");
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

}
