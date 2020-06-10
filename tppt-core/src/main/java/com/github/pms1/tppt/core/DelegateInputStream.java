package com.github.pms1.tppt.core;

import java.io.IOException;
import java.io.InputStream;

class DelegateInputStream extends InputStream {
	final InputStream delegate;

	public DelegateInputStream(InputStream delegate) {
		this.delegate = delegate;
	}

	public int read() throws IOException {
		return delegate.read();
	}

	public int hashCode() {
		return delegate.hashCode();
	}

	public int read(byte[] b) throws IOException {
		return delegate.read(b);
	}

	public boolean equals(Object obj) {
		return delegate.equals(obj);
	}

	public int read(byte[] b, int off, int len) throws IOException {
		return delegate.read(b, off, len);
	}

	public long skip(long n) throws IOException {
		return delegate.skip(n);
	}

	public String toString() {
		return delegate.toString();
	}

	public int available() throws IOException {
		return delegate.available();
	}

	public void close() throws IOException {
		delegate.close();
	}

	public void mark(int readlimit) {
		delegate.mark(readlimit);
	}

	public void reset() throws IOException {
		delegate.reset();
	}

	public boolean markSupported() {
		return delegate.markSupported();
	}

}
