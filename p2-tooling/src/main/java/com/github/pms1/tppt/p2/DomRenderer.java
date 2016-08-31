package com.github.pms1.tppt.p2;

import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMResult;

import org.codehaus.plexus.component.annotations.Component;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

@Component(role = DomRenderer.class)
public class DomRenderer {

	static public enum Options {
		TOP_LEVEL;
	}

	public String render(Node item, Options... options) {
		StringBuilder b = new StringBuilder();
		render(item, b, Arrays.asList(options));
		return b.toString();
	}

	private static String getReplacement(char c, boolean attr) {
		if (attr) {
			switch (c) {
			case (char) 10: // \r
				return "#x0A";
			case '"':
				return "quot"; //$NON-NLS-1$
			}
			if ((int) c < 32)
				throw new Error("C=" + c + " " + (int) c);
		}

		// Encode special XML characters into the equivalent character
		// references.
		// These five are defined by default for all XML documents.
		switch (c) {
		case (char) 13: // \n
			return "#x0D";
		case '<':
			return "lt"; //$NON-NLS-1$
		case '>':
			return "gt"; //$NON-NLS-1$
		case '\'':
			return "apos"; //$NON-NLS-1$
		case '&':
			return "amp"; //$NON-NLS-1$
		}
		return null;
	}

	private static String quote(String text, boolean attr) {
		StringBuilder result = new StringBuilder();

		for (char c : text.toCharArray()) {
			String replacement = getReplacement(c, attr);
			if (replacement != null)
				result.append('&').append(replacement).append(';');
			else
				result.append(c);

		}

		return result.toString();

	}

	private static String quoteAttribute(String nodeValue) {
		return '\"' + quote(nodeValue, true) + '\"';
	}

	private void render(Node node, StringBuilder b, List<Options> options) {
		boolean children;
		switch (node.getNodeType()) {
		case Node.TEXT_NODE:
			Text t = (Text) node;
			b.append(quote(t.getTextContent(), false));
			children = false;
			break;
		case Node.ELEMENT_NODE:
			Element e = (Element) node;
			children = true;
			b.append("<" + e.getNodeName());
			NamedNodeMap attributes = e.getAttributes();
			for (int i = 0; i != attributes.getLength(); ++i) {
				Attr a = (Attr) attributes.item(i);
				b.append(" ").append(a.getNodeName()).append("=").append(quoteAttribute(a.getNodeValue()));
			}
			if (e.getChildNodes().getLength() == 0) {
				b.append("/>");
				return;
			} else {
				b.append(">");
			}
			break;
		case Node.PROCESSING_INSTRUCTION_NODE:
			children = false;
			ProcessingInstruction pi = (ProcessingInstruction) node;
			b.append("<?" + pi.getTarget() + " " + pi.getData() + "?>");
			break;
		case Node.COMMENT_NODE:
			children = false;
			Comment c = (Comment) node;
			b.append("<!--" + c.getTextContent() + "-->");
			break;
		case Node.DOCUMENT_NODE:
			children = true;
			Document d = (Document) node;

			String standalone = d.getXmlStandalone() ? " standalone=\"yes\"" : "";

			if (d.getXmlVersion() == null)
				throw new UnsupportedOperationException();

			// we always transcode to UTF-8
			// if (d.getXmlEncoding() != null &&
			// !Objects.equals(d.getXmlEncoding().toUpperCase(), "UTF-8"))
			// throw new UnsupportedOperationException();

			b.append("<?xml version=\"" + d.getXmlVersion() + "\" encoding=\"UTF-8\"" + standalone + "?>");
			break;
		default:
			throw new Error("Unhandled node " + node.getNodeType() + " " + node);
		}

		if (options.contains(Options.TOP_LEVEL))
			return;

		if (children) {
			for (int i = 0; i != node.getChildNodes().getLength(); ++i)
				render(node.getChildNodes().item(i), b, options);
		} else if (node.getChildNodes().getLength() != 0) {
			throw new IllegalStateException("No children expected for nodeType=" + node.getNodeType() + " " + node);
		}

		switch (node.getNodeType()) {
		case Node.ELEMENT_NODE:
			b.append("</" + node.getNodeName() + ">");
		}
	}

	public String jaxbRender(JAXBContext context, Object o, String name, Options... options) {
		try {
			Marshaller jaxbMarshaller = context.createMarshaller();

			@SuppressWarnings({ "rawtypes", "unchecked" })
			JAXBElement<?> root = new JAXBElement(new QName(name), o.getClass(), o);

			DOMResult r = new DOMResult();

			jaxbMarshaller.marshal(root, r);

			return render(((Document) r.getNode()).getDocumentElement(), options);
		} catch (JAXBException e) {
			throw Throwables.propagate(e);
		}
	}

	public String jaxbRender(JAXBContext context, Object o, Options... options) {
		Preconditions.checkNotNull(o);
		XmlType annotation = Preconditions.checkNotNull(o.getClass().getAnnotation(XmlType.class));
		Preconditions.checkArgument(!annotation.namespace().isEmpty());

		return jaxbRender(context, o, annotation.name(), options);

	}

}
