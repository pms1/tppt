package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.codehaus.plexus.component.annotations.Requirement;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import com.github.pms1.tppt.p2.DomRenderer.DomRendererOptions;
import com.github.pms1.tppt.p2.jaxb.VersionAdapter;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;

public abstract class AbstractRepositoryFactory<T> {
	private final Class<T> clazz;
	protected final String prefix;
	private final Schema schema;
	private final JAXBContext jaxbContext;
	private final String version;

	@Requirement
	DomRenderer renderer;

	protected AbstractRepositoryFactory(JAXBContext jaxbContext, Class<T> clazz, String prefix, String version,
			String xsd) {
		Preconditions.checkNotNull(jaxbContext);
		Preconditions.checkNotNull(clazz);
		this.jaxbContext = jaxbContext;
		this.clazz = clazz;
		this.prefix = prefix;
		this.version = version;
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

	abstract protected void normalize(T t);

	abstract protected T createEmpty();

	final protected void write(T t, OutputStream os) {

		normalize(t);

		try {
			Marshaller marshaller = jaxbContext.createMarshaller();

			@SuppressWarnings({ "rawtypes", "unchecked" })
			JAXBElement<?> root = new JAXBElement(new QName("repository"), t.getClass(), t);

			DOMResult r = new DOMResult();

			marshaller.marshal(root, r);

			Node node = r.getNode();

			ProcessingInstruction instruction = ((Document) node).createProcessingInstruction(prefix + "Repository",
					"version='" + version + "'");
			node.appendChild(instruction);

			node.insertBefore(instruction, node.getFirstChild());

			DomRendererOptions options = new DomRendererOptions();
			options.quote = '\'';
			options.indent = "  ";
			options.attributeSorters.add(e -> {
				switch (e.getNodeName()) {
				case "unit":
					return Arrays.asList("id", "version", "singleton");
				case "update":
					return Arrays.asList("id", "match", "range", "severity", "description");
				case "provided":
					return Arrays.asList("namespace", "name", "version");
				case "required":
					return Arrays.asList("namespace", "name", "version", "range", "optional", "multiple", "greedy",
							"match", "matchParameters", "min", "max");
				default:
					return null;
				}
			});

			try (OutputStreamWriter pw = new OutputStreamWriter(os, Charsets.UTF_8)) {
				renderer.render(pw, node, options);
			}

		} catch (JAXBException | IOException e) {
			throw new RuntimeException(e);
		}

	}

}
