package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMResult;

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

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlType;

@Singleton
@Named("default")
public class DomRenderer {

	static public enum Options {
		TOP_LEVEL;
	}

	public String render(Node item, Options... options) {
		return render(item, createOptions(options));
	}

	public String render(Node item, DomRendererOptions options) {
		StringBuilder b = new StringBuilder();
		try {
			render(item, b, options, "");
		} catch (IOException e) {
			throw new RuntimeException();
		}
		return b.toString();
	}

	public void render(Appendable b, Node item, DomRendererOptions options) throws IOException {
		render(item, b, options, "");
	}

	private DomRendererOptions createOptions(Options[] options) {
		DomRendererOptions o = new DomRendererOptions();
		for (Options o2 : options) {
			switch (o2) {
			case TOP_LEVEL:
				o.recurse = false;
				break;
			}
		}

		return o;
	}

	static public class DomRendererOptions {
		public boolean recurse = true;
		public String indent = null;
		public char quote = '"';

		public List<Function<Element, List<String>>> attributeSorters = new LinkedList<>();
	}

	private static String getReplacement(char c, boolean attr) {
		if (attr) {
			switch (c) {
			case (char) 9: // \t
				return "#x9";
			case (char) 10: // \r
				return "#xA";
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
		case (char) 9: // \t
			return "#x9";
		case (char) 13: // \n
			return "#x0D";
		case (char) 10: // \r
			return "#xA";
		case '"':
			return "quot"; //$NON-NLS-1$
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

	private static String quoteAttribute(String nodeValue, DomRendererOptions options) {
		return options.quote + quote(nodeValue, true) + options.quote;
	}

	private void render(Node node, Appendable b, DomRendererOptions options, String indent) throws IOException {
		boolean children;

		switch (node.getNodeType()) {
		case Node.TEXT_NODE:
			Text t = (Text) node;
			String txt = t.getTextContent();
			if (options.indent != null) {
				txt = txt.trim();
				if (!txt.isEmpty())
					b.append(indent);
			}
			b.append(quote(txt, false));
			if (options.indent != null)
				b.append("\n");
			children = false;
			break;
		case Node.ELEMENT_NODE:
			Element e = (Element) node;
			children = true;
			b.append(indent);
			b.append("<" + e.getNodeName());

			List<String> sort = Collections.emptyList();
			for (Function<Element, List<String>> e1 : options.attributeSorters) {
				List<String> s = e1.apply(e);
				if (s != null) {
					sort = s;
					break;
				}
			}
			NamedNodeMap attributes = e.getAttributes();
			for (String name : sort) {
				Attr a = (Attr) attributes.getNamedItem(name);
				if (a != null)
					b.append(" ").append(a.getNodeName()).append("=").append(quoteAttribute(a.getNodeValue(), options));
			}
			for (int i = 0; i != attributes.getLength(); ++i) {
				Attr a = (Attr) attributes.item(i);
				if (!sort.contains(a.getNodeName()))
					b.append(" ").append(a.getNodeName()).append("=").append(quoteAttribute(a.getNodeValue(), options));
			}
			if (e.getChildNodes().getLength() == 0) {
				b.append("/>");
				children = false;
			} else {
				b.append(">");
			}
			if (options.indent != null)
				b.append("\n");
			break;
		case Node.PROCESSING_INSTRUCTION_NODE:
			children = false;
			ProcessingInstruction pi = (ProcessingInstruction) node;
			b.append("<?" + pi.getTarget() + " " + pi.getData() + "?>");
			if (options.indent != null)
				b.append("\n");
			break;
		case Node.COMMENT_NODE:
			children = false;
			Comment c = (Comment) node;
			b.append("<!--" + c.getTextContent() + "-->");
			break;
		case Node.DOCUMENT_NODE:
			children = true;
			Document d = (Document) node;

			String standalone = d.getXmlStandalone() ? " standalone=" + options.quote + "yes" + options.quote : "";

			if (d.getXmlVersion() == null)
				throw new UnsupportedOperationException();

			// we always transcode to UTF-8
			// if (d.getXmlEncoding() != null &&
			// !Objects.equals(d.getXmlEncoding().toUpperCase(), "UTF-8"))
			// throw new UnsupportedOperationException();

			b.append("<?xml version=" + options.quote + d.getXmlVersion() + options.quote + " encoding=" + options.quote
					+ "UTF-8" + options.quote + standalone + "?>");
			if (options.indent != null)
				b.append("\n");
			break;
		default:
			throw new Error("Unhandled node " + node.getNodeType() + " " + node);
		}

		if (options.recurse) {
			if (children) {
				for (int i = 0; i != node.getChildNodes().getLength(); ++i)
					render(node.getChildNodes().item(i), b, options,
							options.indent != null && !(node instanceof Document) ? indent + options.indent : indent);
			} else if (node.getChildNodes().getLength() != 0) {
				throw new IllegalStateException("No children expected for nodeType=" + node.getNodeType() + " " + node);
			}
		} else {
			if (children)
				b.append("...");
		}

		switch (node.getNodeType()) {
		case Node.ELEMENT_NODE:
			Element e = (Element) node;
			if (e.getChildNodes().getLength() != 0) {
				b.append(indent);
				b.append("</" + node.getNodeName() + ">");
				if (options.indent != null)
					b.append("\n");
			}
			break;
		default:
			break;
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
