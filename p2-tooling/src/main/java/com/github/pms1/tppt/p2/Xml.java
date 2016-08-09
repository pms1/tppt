package com.github.pms1.tppt.p2;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

final class Xml {
	private Xml() {

	}

	static class Holder {
		final static DocumentBuilderFactory factory;
		static {
			factory = DocumentBuilderFactory.newInstance();
			assert factory != null;
			factory.setNamespaceAware(true);
		}
	}

	static DocumentBuilder createDocumentBuilder() {
		try {
			DocumentBuilder db = Holder.factory.newDocumentBuilder();
			db.setEntityResolver(new EntityResolver() {

				@Override
				public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
					// FIXME
					throw new Error();
				}
			});
			// The default handler of Oracle's JDK writes to System.err, so we
			// have to overwrite it.
			db.setErrorHandler(new ErrorHandler() {

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

			});
			return db;
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}

	}
}
