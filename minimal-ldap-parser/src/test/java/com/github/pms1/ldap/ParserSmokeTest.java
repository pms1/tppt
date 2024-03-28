package com.github.pms1.ldap;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.junit.Test;

public class ParserSmokeTest {

	void run(String text) {
		Lexer lexer = new Rfc4515Lexer(CharStreams.fromString(text));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		Rfc4515Parser parser = new Rfc4515Parser(tokens);
		parser.filterEOF();
	}

	@Test
	public void t1() {
		run("(&(a=5))");
		run("(a=5)");
	}
}
