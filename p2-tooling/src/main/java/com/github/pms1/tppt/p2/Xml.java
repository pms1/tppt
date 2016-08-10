package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.google.common.base.Preconditions;

final class Xml {
	private Xml() {

	}

	private static final class ClasspathResourceLSResourceResolver implements LSResourceResolver {
		private final Class<?> clazz;

		ClasspathResourceLSResourceResolver(Class<?> clazz) {
			Preconditions.checkNotNull(clazz);
			this.clazz = clazz;
		}

		@Override
		public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
				String baseURI) {

			if (type.equals("http://www.w3.org/2001/XMLSchema")
					&& /* namespaceURI == null && */ publicId == null
			/* && baseURI == null */) {
				return new LSInput() {

					@Override
					public void setSystemId(String systemId) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void setStringData(String stringData) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void setPublicId(String publicId) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void setEncoding(String encoding) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void setCharacterStream(Reader characterStream) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void setCertifiedText(boolean certifiedText) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void setByteStream(InputStream byteStream) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void setBaseURI(String baseURI) {
						throw new UnsupportedOperationException();
					}

					@Override
					public String getSystemId() {
						return systemId;
					}

					@Override
					public String getStringData() {
						return null;
					}

					@Override
					public String getPublicId() {
						return null;
					}

					@Override
					public String getEncoding() {
						return null;
					}

					@Override
					public Reader getCharacterStream() {
						return null;
					}

					@Override
					public boolean getCertifiedText() {
						throw new UnsupportedOperationException();
					}

					@Override
					public InputStream getByteStream() {
						return clazz.getResourceAsStream(systemId);
					}

					@Override
					public String getBaseURI() {
						return baseURI;
					}
				};
			} else {
				throw new Error("Cannot locate resource (type='" + type + "', namespaceURI='" + namespaceURI
						+ "', publicId='" + publicId + "', systemId='" + systemId + "', baseURI='" + baseURI + "')");
			}
		}
	}

	static class Holder {
		final static DocumentBuilderFactory factory;
		static {
			factory = DocumentBuilderFactory.newInstance();
			assert factory != null;
			factory.setNamespaceAware(true);
		}
	}

	// The default handler of Oracle's JDK writes to System.err, so we
	// have to overwrite it.
	private static ErrorHandler throwingErrorHandler = new ErrorHandler() {

		@Override
		public void warning(SAXParseException exception) throws SAXException {
			// may be to strict, not seen in practice (yet)
			throw exception;
		}

		@Override
		public void error(SAXParseException exception) throws SAXException {
			// may be to strict, not seen in practice (yet)
			throw exception;
		}

		@Override
		public void fatalError(SAXParseException exception) throws SAXException {
			throw exception;

		}

	};

	// We never, ever want to try to load external resources
	private static final EntityResolver throwingEntityResolver = new EntityResolver() {

		@Override
		public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
			throw new Error("Cannot locate resource (publicId='" + publicId + "', systemId='" + systemId + "')");
		}
	};

	private static void harden(DocumentBuilder db) {
		db.setEntityResolver(throwingEntityResolver);
		db.setErrorHandler(throwingErrorHandler);
	}

	static Schema createSchema(Class<?> clazz, String resource) {
		SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
		schemaFactory.setErrorHandler(throwingErrorHandler);
		schemaFactory.setResourceResolver(new ClasspathResourceLSResourceResolver(clazz));
		try (InputStream is = clazz.getResourceAsStream(resource)) {
			if (is == null)
				throw new Error("C=" + clazz + " " + resource);
			return schemaFactory.newSchema(new Source[] { new StreamSource(is) });
		} catch (SAXException | IOException e) {
			throw new RuntimeException("Creation of schema (" + clazz.getName() + "," + resource + " failed"
					+ (e.getMessage() != null ? ": " + e.getMessage() : ""), e);
		}
	}

	static DocumentBuilder createValidatingDocumentBuilder(Class<?> clazz, String resource) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setValidating(false);

			SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
			schemaFactory.setErrorHandler(throwingErrorHandler);
			schemaFactory.setResourceResolver(new ClasspathResourceLSResourceResolver(clazz));
			try (InputStream is = clazz.getResourceAsStream(resource)) {
				dbf.setSchema(schemaFactory.newSchema(new Source[] { new StreamSource(is) }));
			}

			DocumentBuilder db = dbf.newDocumentBuilder();

			harden(db);

			return db;
		} catch (IOException | ParserConfigurationException | SAXException e) {
			throw new RuntimeException("Creation of validating parser(" + clazz.getName() + "," + resource + " failed"
					+ (e.getMessage() != null ? ": " + e.getMessage() : ""), e);
		}
	}

	static DocumentBuilder createDocumentBuilder() {
		try {
			DocumentBuilder db = Holder.factory.newDocumentBuilder();

			harden(db);

			return db;
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}

	}
}
