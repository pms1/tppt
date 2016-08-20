package com.github.pms1.ldap;

import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import com.github.pms1.ldap.Rfc4515Parser.AndContext;
import com.github.pms1.ldap.Rfc4515Parser.AttrContext;
import com.github.pms1.ldap.Rfc4515Parser.AttributedescriptionContext;
import com.github.pms1.ldap.Rfc4515Parser.FilterContext;
import com.github.pms1.ldap.Rfc4515Parser.FilterEOFContext;
import com.github.pms1.ldap.Rfc4515Parser.FiltercompContext;
import com.github.pms1.ldap.Rfc4515Parser.FilterlistContext;
import com.github.pms1.ldap.Rfc4515Parser.FiltertypeContext;
import com.github.pms1.ldap.Rfc4515Parser.ItemContext;
import com.github.pms1.ldap.Rfc4515Parser.NotContext;
import com.github.pms1.ldap.Rfc4515Parser.OrContext;
import com.github.pms1.ldap.Rfc4515Parser.SimpleContext;

public class SearchFilterParser {
	private final boolean lenient;

	public SearchFilterParser() {
		this(false);
	}

	private SearchFilterParser(boolean lenient) {
		this.lenient = lenient;
	}

	public SearchFilterParser lenient() {
		return new SearchFilterParser(true);
	}

	public SearchFilter parse(String searchFilter) {
		BaseErrorListener errorListener = new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {

				throw new SearchFilterParseException(
						"expression '" + searchFilter + "':" + line + ":" + charPositionInLine + ": " + msg, e);
			};
		};

		if (false) {
			Lexer lexer = new Rfc4515Lexer(new ANTLRInputStream(searchFilter));
			lexer.removeErrorListeners();
			lexer.addErrorListener(errorListener);

			for (Token t : lexer.getAllTokens()) {
				System.err.println(t + " " + Rfc4515Lexer.VOCABULARY.getDisplayName(t.getType()) + " "
						+ Rfc4515Lexer.VOCABULARY.getLiteralName(t.getType()) + " "
						+ Rfc4515Lexer.VOCABULARY.getSymbolicName(t.getType()));
			}
		}

		Lexer lexer = new Rfc4515Lexer(new ANTLRInputStream(searchFilter));
		lexer.removeErrorListeners();
		lexer.addErrorListener(errorListener);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		Rfc4515Parser parser = new Rfc4515Parser(tokens);
		parser.lenient = lenient;
		parser.removeErrorListeners();
		parser.addErrorListener(errorListener);

		FilterEOFContext tree = parser.filterEOF();

		return translate(tree);
	}

	private SearchFilter translate(FilterEOFContext context) {
		return translate(context.filter());
	}

	private SearchFilter translate(FilterContext context) {
		return translate(context.filtercomp());
	}

	private SearchFilter translate(FiltercompContext context) {
		if (context.and() != null) {
			return translate(context.and());
		} else if (context.or() != null) {
			return translate(context.or());
		} else if (context.not() != null) {
			return translate(context.not());
		} else if (context.item() != null) {
			return translate(context.item());
		} else {
			throw new IllegalStateException();
		}
	}

	private SearchFilter translate(ItemContext context) {
		if (context.simple() != null) {
			return translate(context.simple());
		} else {
			throw new IllegalStateException();
		}

	}

	private SearchFilter translate(SimpleContext simple) {
		Attribute attribute = translate(simple.attr());
		FilterType filterType = translate(simple.filtertype());
		return new SimpleSearchFilter(attribute, filterType, simple.assertionvalue().getText());
	}

	private FilterType translate(FiltertypeContext filtertype) {
		if (filtertype.equal() != null)
			return FilterType.EQUAL;
		throw new IllegalStateException();
	}

	private Attribute translate(AttrContext attr) {
		return translate(attr.attributedescription());
	}

	private Attribute translate(AttributedescriptionContext attributedescription) {
		if (attributedescription.attributetype().oid() == null)
			throw new IllegalStateException();
		if (attributedescription.attributetype().oid().descr() == null)
			throw new IllegalStateException();
		if (attributedescription.attributetype().oid().descr().keystring() == null)
			throw new IllegalStateException();

		String attribute = attributedescription.attributetype().oid().descr().keystring().getText();

		if (!attributedescription.options().children.isEmpty())
			throw new IllegalStateException("" + attributedescription.options().getTokens(0));

		return new AttributeDescription(attribute);
	}

	private SearchFilter translate(AndContext context) {
		return new AndSearchFilter(translate(context.filterlist()));
	}

	private SearchFilter translate(OrContext context) {
		return new OrSearchFilter(translate(context.filterlist()));
	}

	private SearchFilter translate(NotContext context) {
		return new NotSearchFilter(translate(context.filter()));
	}

	private List<SearchFilter> translate(FilterlistContext context) {
		return context.filter().stream().map(this::translate).collect(Collectors.toList());
	}
}
