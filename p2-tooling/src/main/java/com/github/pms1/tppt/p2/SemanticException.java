package com.github.pms1.tppt.p2;

public abstract class SemanticException extends RuntimeException {
	public SemanticException(FileId file, String text) {
		super(file + ": " + text);
	}

	public SemanticException(FileId file, String text, Throwable cause) {
		super(file + ": " + text, cause);
	}

}
