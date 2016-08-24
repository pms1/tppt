package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import com.github.pms1.tppt.p2.jaxb.VersionAdapter;

public abstract class AbstractRepositoryFactory<T> {
	private final Class<T> clazz;
	private final String prefix;
	private final String content;
	private final Schema schema;

	protected AbstractRepositoryFactory(Class<T> clazz, String prefix, String content, String xsd) {
		Objects.requireNonNull(clazz);
		this.clazz = clazz;
		this.prefix = prefix;
		this.content = content;
		this.schema = Xml.createSchema(VersionAdapter.class, xsd);
	}

	protected T read(InputStream is) {
		try {
			JAXBContext context = JAXBContext.newInstance(clazz);
			Unmarshaller unmarshaller = context.createUnmarshaller();
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

	protected T readRepository(Path p) throws IOException {
		Properties p2index = new Properties();

		try (InputStream is = Files.newInputStream(p.resolve("p2.index"))) {
			p2index.load(is);
		}

		if (!Objects.equals(p2index.getProperty("version", null), "1"))
			throw new Error();

		T data = null;
		for (String f : p2index.getProperty(prefix + ".repository.factory.order", "").split(",")) {
			if (f.equals(content + ".xml")) {
				try (InputStream is = Files.newInputStream(p.resolve(content + ".xml"))) {
					return read(is);
				}
			} else if (f.equals("!")) {
				throw new Error();
			} else {
				throw new Error("f=" + f);
			}
		}

		return data;
	}

}
