package com.github.pms1.tppt.p2;

import java.io.InputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.codehaus.plexus.component.annotations.Requirement;

import com.github.pms1.tppt.p2.jaxb.VersionAdapter;
import com.google.common.base.Preconditions;

public abstract class AbstractRepositoryFactory<T> {
	private final Class<T> clazz;
	protected final String prefix;
	private final Schema schema;
	private final JAXBContext jaxbContext;

	@Requirement
	DomRenderer renderer;

	protected AbstractRepositoryFactory(JAXBContext jaxbContext, Class<T> clazz, String prefix, String xsd) {
		Preconditions.checkNotNull(jaxbContext);
		Preconditions.checkNotNull(clazz);
		this.jaxbContext = jaxbContext;
		this.clazz = clazz;
		this.prefix = prefix;
		this.schema = Xml.createSchema(VersionAdapter.class, xsd);
	}

	protected T read(InputStream is) {
		try {
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			unmarshaller.setSchema(schema);
			unmarshaller.setEventHandler(new ValidationEventHandler() {

				@Override
				public boolean handleEvent(ValidationEvent event) {
					return false;
				}
			});
			return unmarshaller.unmarshal(new StreamSource(is), clazz).getValue();
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}
	}

}
