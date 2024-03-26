package com.github.pms1.tppt.mirror;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import com.github.pms1.tppt.mirror.jaxb.Mirror;

class MirrorsParser {

	private static class Holder {
		static DocumentBuilderFactory dbf;
		static {
			dbf = DocumentBuilderFactory.newDefaultNSInstance();
			try {
				dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				dbf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
				dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
				dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				dbf.setXIncludeAware(false);
			} catch (ParserConfigurationException e) {
				throw new RuntimeException(e);
			}
		}

	}

	static Mirror[] parse(Path of) {
		DocumentBuilder documentBuilder;
		try {
			documentBuilder = Holder.dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}

		Document document;
		try {
			document = documentBuilder.parse(of.toFile());
		} catch (SAXException | IOException e) {
			throw new RuntimeException(e);
		}

		Element root = document.getDocumentElement();
		if (!root.getNodeName().equals("mirrors"))
			throw new IllegalArgumentException(of + ": top level element expected to be 'mirrors'");
		if (root.getAttributes().getLength() != 0)
			throw new IllegalArgumentException(of + ": top level element not expected to have attributes");

		List<Mirror> mirrors = new ArrayList<>();
		NodeList childNodes = document.getDocumentElement().getChildNodes();
		for (int i = 0; i != childNodes.getLength(); ++i) {
			Node item = childNodes.item(i);
			switch (item.getNodeType()) {
			case Node.TEXT_NODE:
				Text t = (Text) item;
				if (!t.getWholeText().isBlank())
					throw new IllegalArgumentException(
							of + ": unexpected non empty text node: >" + t.getWholeText() + "<");
				break;
			case Node.ELEMENT_NODE:
				Element e = (Element) item;
				if (!e.getNodeName().equals("mirror"))
					throw new IllegalArgumentException(of + ": element exepected to be 'mirror'");
				NamedNodeMap attributes = e.getAttributes();
				String url = null;
				for (int j = 0; j != attributes.getLength(); ++j) {
					Attr a = (Attr) attributes.item(j);
					switch (a.getName()) {
					case "url":
						url = a.getTextContent();
						break;
					case "label":
						break;
					default:
						throw new IllegalArgumentException(of + ": unexpected attribute " + a.getName());
					}
				}
				if (url != null) {
					Mirror mirror = new Mirror();
					mirror.url = Uris.normalizeDirectory(URI.create(url));
					mirrors.add(mirror);
				}
				break;
			default:
				throw new IllegalArgumentException(of + ": unexpected node type " + item.getNodeType());
			}
		}

		return mirrors.toArray(Mirror[]::new);
	}
}
