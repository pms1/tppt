package com.github.pms1.tppt.p2;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.google.common.base.Throwables;

public abstract class CompositeRepositoryFactory extends AbstractRepositoryFactory<CompositeRepository> {

	protected CompositeRepositoryFactory(String prefix) {
		super(getJaxbContext(), CompositeRepository.class, prefix, "compositeRepository.xsd");
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

}
