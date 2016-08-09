package com.github.pms1.tppt.p2;

public final class UnhandledException extends RuntimeException {
	public UnhandledException(FileId file, String text) {
		super(file + ": Unhandled content: " + text);
	}
}
