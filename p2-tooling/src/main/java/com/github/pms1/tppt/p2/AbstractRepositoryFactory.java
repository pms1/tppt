package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.codehaus.plexus.component.annotations.Requirement;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import com.github.pms1.tppt.p2.jaxb.VersionAdapter;
import com.google.common.base.Preconditions;

public abstract class AbstractRepositoryFactory<T> {
	private final Class<T> clazz;
	private final String prefix;
	private final String content;
	private final Schema schema;
	private final JAXBContext jaxbContext;

	@Requirement
	DomRenderer renderer;

	protected AbstractRepositoryFactory(JAXBContext jaxbContext, Class<T> clazz, String prefix, String content,
			String xsd) {
		Preconditions.checkNotNull(jaxbContext);
		Preconditions.checkNotNull(clazz);
		this.jaxbContext = jaxbContext;
		this.clazz = clazz;
		this.prefix = prefix;
		this.content = content;
		this.schema = Xml.createSchema(VersionAdapter.class, xsd);
	}

	protected void write(T t, OutputStream os) {

		try {
			Marshaller marshaller = jaxbContext.createMarshaller();

			@SuppressWarnings({ "rawtypes", "unchecked" })
			JAXBElement<?> root = new JAXBElement(new QName("repository"), t.getClass(), t);

			DOMResult r = new DOMResult();

			marshaller.marshal(root, r);

			Node node = r.getNode();

			ProcessingInstruction instruction = ((Document) node).createProcessingInstruction(prefix + "Repository",
					"version='1.0.0'");
			node.appendChild(instruction);

			node.insertBefore(instruction, node.getFirstChild());

			// write the content into xml file
			// FIXME: use a formatter that looks like eclipse generated
			// repositories
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(r.getNode());
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			transformer.transform(source, new StreamResult(os));

		} catch (JAXBException | TransformerException e) {
			throw new RuntimeException(e);
		}

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
