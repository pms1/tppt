package com.github.pms1.ldap;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Test;

import com.github.pms1.ldap.Rfc4515Lexer;
import com.github.pms1.ldap.Rfc4515Parser;

public class ParserSmokeTest {

	void run(String text) {
		Lexer lexer = new Rfc4515Lexer( new ANTLRInputStream(text	));
	    CommonTokenStream tokens = new CommonTokenStream( lexer );
	    Rfc4515Parser parser = new Rfc4515Parser( tokens );
	    ParseTree tree = parser.filterEOF();
	}
	
	@Test
	public void t1() {
		run("(&(a=5))");
		run("(a=5)");
	}
}
