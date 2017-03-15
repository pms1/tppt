package com.github.pms1.tppt.p2;

import java.io.OutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import com.github.pms1.tppt.p2.jaxb.composite.Children;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.composite.Properties;
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

	protected void write(CompositeRepository t, OutputStream os) {

		normalize(t);

		try {
			Marshaller marshaller = getJaxbContext().createMarshaller();

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

	protected void normalize(CompositeRepository t) {
		Properties properties = t.getProperties();
		if (properties != null)
			properties.setSize(properties.getProperty().size());

		Children children = t.getChildren();
		if (children != null)
			children.setSize(children.getChild().size());
	}
}
