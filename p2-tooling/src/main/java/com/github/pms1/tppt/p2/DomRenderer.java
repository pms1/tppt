package com.github.pms1.tppt.p2;

import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class DomRenderer {

	public String render(Node item) {
		StringBuilder b = new StringBuilder();
		render(item, b);
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

	private void render(Node node, StringBuilder b) {
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
			b.append(">");
			break;
		case Node.COMMENT_NODE:
			children = false;
			Comment c = (Comment) node;
			b.append("<!--" + c.getTextContent() + "-->");
			break;
		default:
			throw new Error("Unhandled node " + node.getNodeType() + " " + node);
		}
		if (children)
			for (int i = 0; i != node.getChildNodes().getLength(); ++i)
				render(node.getChildNodes().item(i), b);
		else if (node.getChildNodes().getLength() != 0)
			throw new IllegalStateException("No children expected for nodeType=" + node.getNodeType() + " " + node);
		switch (node.getNodeType()) {
		case Node.ELEMENT_NODE:
			b.append("</" + node.getNodeName() + ">");
		}
	}

}
