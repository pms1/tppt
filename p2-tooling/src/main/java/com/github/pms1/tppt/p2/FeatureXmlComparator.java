package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.codehaus.plexus.component.annotations.Component;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

@Component(hint = "feature.xml", role = FileComparator.class)
public class FeatureXmlComparator implements FileComparator {

	private DomRenderer domRenderer = new DomRenderer();

	@Override
	public void compare(FileId file1, Path p1, FileId file2, Path p2, Consumer<FileDelta> dest) throws IOException {
		Document d1;
		Document d2;

		try (InputStream is = Files.newInputStream(p1)) {
			try {
				d1 = Xml.createDocumentBuilder().parse(is);
			} catch (SAXException e) {
				throw new UnparseableXmlException(file1, e);
			}
		}

		try (InputStream is = Files.newInputStream(p2)) {
			try {
				d2 = Xml.createDocumentBuilder().parse(is);
			} catch (SAXException e) {
				throw new UnparseableXmlException(file2, e);
			}
		}

		if (!d1.getDocumentElement().getTagName().equals("feature"))
			throw new Error();

		if (!d2.getDocumentElement().getTagName().equals("feature"))
			throw new Error();

		compareAttributes(d1.getDocumentElement(), d2.getDocumentElement(), new AttributesDeltaReporter() {

			@Override
			public void added(String key, String value) {
				dest.accept(new FileDelta(file1, file2, "Added feature attribute: {0} {1} ", key, value));
			}

			@Override
			public void removed(String key) {
				dest.accept(new FileDelta(file1, file2, "Removed feature attribute: {0}", key));
			}

			@Override
			public void changed(String key, String left, String right) {
				if (key.equals("version"))
					dest.accept(new FeatureVersionDelta(file1, file2, VersionParser.valueOf(left),
							VersionParser.valueOf(right)));
				else
					dest.accept(
							new FileDelta(file1, file2, "Changed feature attribute: {0} {1} -> {2}", key, left, right));
			}

		});

		Nodes n1 = new Nodes();
		Nodes n2 = new Nodes();
		processRootElement(file1, d1.getDocumentElement().getChildNodes(), n1);
		processRootElement(file2, d2.getDocumentElement().getChildNodes(), n2);

		comparePlugins2(n1.plugins, n2.plugins, new ElementDeltaReporter() {

			@Override
			public void added(String e) {
				dest.accept(new FileDelta(file1, file2, "Added plugin: {0}", e));
			}

			@Override
			public void removed(String e) {
				dest.accept(new FileDelta(file1, file2, "Removed plugin: {0}", e));
			}

		}, new DeltaReporter(file1, file2, dest));
		comparePlugins(n1.includes, n2.includes, new ElementDeltaReporter() {

			@Override
			public void added(String e) {
				dest.accept(new FileDelta(file1, file2, "Added includes: {0}", e));
			}

			@Override
			public void removed(String e) {
				dest.accept(new FileDelta(file1, file2, "Removed includes: {0}", e));
			}

		}, new AttributesDeltaReporter() {

			@Override
			public void added(String key, String value) {
				dest.accept(new FileDelta(file1, file2, "Added includes attribute: {0} {1}", key, value));
			}

			@Override
			public void removed(String key) {
				dest.accept(new FileDelta(file1, file2, "Removed includes attribute: {0} ", key));
			}

			@Override
			public void changed(String key, String left, String right) {
				dest.accept(
						new FileDelta(file1, file2, "Changed includes attribute: {0} {1} -> {2}", key, left, right));
			}

		});
		compareImports(n1.imports, n2.imports, (d, p) -> dest.accept(new FileDelta(file1, file2, d, p)));
	}

	static class DeltaReporter {
		final FileId baseline;
		final FileId current;
		final Consumer<FileDelta> dest;

		DeltaReporter(FileId baseline, FileId current, Consumer<FileDelta> dest) {
			this.baseline = baseline;
			this.current = current;
			this.dest = dest;
		}

		public void fileDelta(String description, Object... parameters) {
			dest.accept(new FileDelta(baseline, current, description, parameters));
		}

		public void pluginVersionDelta(String id, String left, String right) {
			dest.accept(new FeaturePluginVersionDelta(baseline, current, id, VersionParser.valueOf(left),
					VersionParser.valueOf(right)));
		}
	}

	private void comparePlugins2(Map<String, Multimap<String, Element>> baseline,
			Map<String, Multimap<String, Element>> current, ElementDeltaReporter elementDeltaReporter,
			DeltaReporter deltaReporter) {

		for (String id : Sets.union(baseline.keySet(), current.keySet())) {
			Multimap<String, Element> b = baseline.get(id);
			if (b == null)
				b = HashMultimap.create();
			Multimap<String, Element> c = current.get(id);
			if (c == null)
				c = HashMultimap.create();

			if (b.size() == 1 && c.size() == 1) {
				compareAttributes(Iterables.getOnlyElement(b.values()), Iterables.getOnlyElement(c.values()),
						new AttributesDeltaReporter() {

							@Override
							public void removed(String key) {
								deltaReporter.fileDelta("Plugin {0} attribute {1} removed", id, key);
							}

							@Override
							public void changed(String key, String left, String right) {
								if (key.equals("version")) {
									deltaReporter.pluginVersionDelta(id, left, right);
								} else {
									deltaReporter.fileDelta("Plugin {0} attribute {1} changed {2} -> {3}", id, key,
											left, right);
								}
							}

							@Override
							public void added(String key, String value) {
								deltaReporter.fileDelta("Plugin {0} attribute {1} / {2} added", id, key, value);
							}
						});
			} else {
				for (Element e : b.values())
					deltaReporter.fileDelta("Plugin removed: {0}", domRenderer.render(e));
				for (Element e : c.values())
					deltaReporter.fileDelta("Plugin added: {0}", domRenderer.render(e));
			}
		}

	}

	private void compareImports(List<Element> imports, List<Element> imports2, BiConsumer<String, Object[]> consumer) {

		List<Element> e2 = new ArrayList<>(imports2);

		for (Element e : imports) {
			String re = domRenderer.render(e);

			boolean found = false;
			for (Iterator<Element> i = e2.iterator(); i.hasNext();) {

				String r = domRenderer.render(i.next());

				if (re.equals(r)) {
					i.remove();
					found = true;
					break;
				}
			}

			if (found)
				continue;

			consumer.accept("removed import {0}", new Object[] { re });
		}

		for (Element e : e2) {
			consumer.accept("Added import {0}", new Object[] { domRenderer.render(e) });
		}
	}

	// private void compareText(Element d1, Element d2) {
	// if (d1 == null && d2 == null) {
	// return;
	// } else if (d2 != null) {
	// System.err.println("ADDED TEXT " + d2);
	// } else if (d1 != null) {
	// System.err.println("REMOVED TEXT " + d2);
	// }
	//
	// if (!d1.getTextContent().equals(d2.getTextContent())) {
	// System.err.println("CONTENT CHANGED");
	// }
	// System.err.println("D1 " + d1.getTextContent());
	// }

	private void comparePlugins(Map<String, Element> plugins1, Map<String, Element> plugins2,
			ElementDeltaReporter elementDeltaReporter, AttributesDeltaReporter attributesDeltaReporter) {

		for (String id : Sets.union(plugins1.keySet(), plugins2.keySet())) {
			Element e1 = plugins1.get(id);
			Element e2 = plugins2.get(id);
			if (e1 == null) {
				elementDeltaReporter.added(domRenderer.render(e2));
			} else if (e2 == null) {
				elementDeltaReporter.removed(domRenderer.render(e1));
			} else {
				compareAttributes(e1, e2, attributesDeltaReporter);
			}
		}
	}

	static class DuplicatePluginException extends SemanticException {

		public DuplicatePluginException(FileId file, String text) {
			super(file, text);
		}

	}

	static class PluginChildElementsException extends SemanticException {

		public PluginChildElementsException(FileId file, String text) {
			super(file, text);
		}

	}

	static class PluginMissingAttributeException extends SemanticException {

		public PluginMissingAttributeException(FileId file, String text) {
			super(file, text);
		}

	}

	static class DuplicateElementException extends SemanticException {

		public DuplicateElementException(FileId file, String text) {
			super(file, text);
		}

	}

	static class UnexpectedTextException extends SemanticException {

		public UnexpectedTextException(FileId file, String text) {
			super(file, text);
		}

	}

	static class UnparseableXmlException extends SemanticException {

		public UnparseableXmlException(FileId file, SAXException parent) {
			super(file, parent.toString(), parent);
		}

	}

	private static class Nodes {
		Map<String, Multimap<String, Element>> plugins = new HashMap<>();
		Map<String, Element> includes = new HashMap<>();
		List<Element> imports = new ArrayList<>();
		Map<String, String> other = new HashMap<>();
	}

	private void processRootElement(FileId file, NodeList nl, Nodes nodes) {
		for (int i = 0; i != nl.getLength(); ++i) {
			Node item = nl.item(i);
			switch (item.getNodeType()) {
			case Node.TEXT_NODE:
				Text t = ((Text) item);
				for (char c : t.getNodeValue().toCharArray()) {
					if (!Character.isWhitespace(c)) {
						throw new UnexpectedTextException(file,
								"Unexpected non-whitespace between elements: " + quoteForDisplay(t.getNodeValue()));
					}
				}
				break;
			case Node.ELEMENT_NODE:
				Element e = ((Element) item);
				switch (e.getTagName()) {
				case "plugin":
					if (e.getChildNodes().getLength() != 0)
						throw new PluginChildElementsException(file, "<plugin> element has children");
					Attr id = e.getAttributeNode("id");
					if (id == null || id.getValue().isEmpty())
						throw new PluginMissingAttributeException(file, "<plugin> has no or empty attribute 'id'");
					Attr version = e.getAttributeNode("version");
					if (version == null || version.getValue().isEmpty())
						throw new PluginMissingAttributeException(file, "<plugin> has no attribute 'version'");
					Multimap<String, Element> v2e = nodes.plugins.get(id.getValue());
					if (v2e == null) {
						v2e = HashMultimap.create();
						nodes.plugins.put(id.getValue(), v2e);
					}
					v2e.put(version.getValue(), e);
					break;
				case "requires":
					processRequires(file, e.getChildNodes(), nodes);
					break;
				// throw new Error("req " + domRenderer.render(e));
				case "includes":
					if (e.getChildNodes().getLength() != 0)
						throw new PluginChildElementsException(file, "<includes> element has children");
					id = e.getAttributeNode("id");
					if (id == null || id.getValue().isEmpty())
						throw new PluginMissingAttributeException(file, "<includes> has no or empty attribute 'id'");
					version = e.getAttributeNode("version");
					if (version == null || version.getValue().isEmpty())
						throw new PluginMissingAttributeException(file, "<includes> has no attribute 'version'");
					Object old = nodes.includes.put(featureId(id, version), e);
					if (old != null)
						throw new UnhandledException(file, "Duplicate <includes> '" + id + "' '" + version + "'");
					break;
				case "description":
				case "copyright":
				case "license":
				default:
					old = nodes.other.put(e.getTagName(), domRenderer.render(e));
					if (old != null)
						throw new DuplicateElementException(file, "duplicate element <" + e.getTagName() + ">");
				}
				break;
			case Node.COMMENT_NODE:
				break;
			default:
				throw new UnhandledException(file, "Unhandled node " + item.getNodeType() + " " + item);
			}
		}
	}

	private void processRequires(FileId file, NodeList nl, Nodes nodes) {
		for (int i = 0; i != nl.getLength(); ++i) {
			Node item = nl.item(i);
			switch (item.getNodeType()) {
			case Node.TEXT_NODE:
				Text t = ((Text) item);
				for (char c : t.getNodeValue().toCharArray()) {
					if (!Character.isWhitespace(c)) {
						throw new UnexpectedTextException(file,
								"Unexpected non-whitespace between elements: " + quoteForDisplay(t.getNodeValue()));
					}
				}
				break;
			case Node.ELEMENT_NODE:
				Element e = ((Element) item);
				switch (e.getTagName()) {
				case "import":
					nodes.imports.add(e);
					break;
				default:
					throw new Error();
				}
				break;
			case Node.COMMENT_NODE:
				break;
			default:
				throw new UnhandledException(file, "Unhandled node " + item.getNodeType() + " " + item);
			}
		}

	}

	private static String quoteForDisplay(String nodeValue) {
		StringBuilder b = new StringBuilder();
		for (char c : nodeValue.toCharArray()) {
			switch (c) {
			case '\\':
				b.append("\\\\");
				break;
			case '\n':
				b.append("\\n");
				break;
			case '\r':
				b.append("\\r");
				break;
			default:
				b.append(c);
				break;
			}
		}
		return b.toString();
	}

	private String featureId(Attr idAttr, Attr versionAttr) {
		Preconditions.checkNotNull(idAttr);
		String id = idAttr.getValue();
		Preconditions.checkArgument(!id.contains("\t"));
		Preconditions.checkNotNull(versionAttr);
		String version = versionAttr.getValue();
		Preconditions.checkArgument(!version.contains("\t"));
		return id + "\t" + version;
	}

	static interface ElementDeltaReporter {
		void added(String e);

		void removed(String e);
	}

	static interface AttributesDeltaReporter {
		void added(String key, String value);

		void removed(String key);

		void changed(String key, String left, String right);
	}

	private void compareAttributes(Element e1, Element e2, AttributesDeltaReporter c) {
		NamedNodeMap attrs1 = e1.getAttributes();
		NamedNodeMap attrs2 = e2.getAttributes();

		for (int i = 0; i != attrs1.getLength(); ++i) {
			Attr a1 = (Attr) attrs1.item(i);

			Attr a2 = (Attr) attrs2.getNamedItemNS(a1.getNamespaceURI(), a1.getLocalName());

			if (a2 == null) {
				c.removed(a1.getName());
			} else {
				if (!Objects.equals(a1.getValue(), a2.getValue()))
					c.changed(a1.getName(), a1.getValue(), a2.getValue());
			}
		}

		for (int i = 0; i != attrs2.getLength(); ++i) {
			Attr a2 = (Attr) attrs2.item(i);

			Attr a1 = (Attr) e2.getAttributes().getNamedItemNS(a2.getNamespaceURI(), a2.getLocalName());

			if (a1 == null) {
				c.added(a2.getName(), a2.getValue());
			}
		}
	}

}