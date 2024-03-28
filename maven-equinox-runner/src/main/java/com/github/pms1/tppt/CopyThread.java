package com.github.pms1.tppt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class CopyThread extends Thread {
	private final InputStream is;
	private final OutputStream os;

	CopyThread(InputStream is, OutputStream os) {
		this.is = is;
		this.os = os;
	}

	@Override
	public void run() {
		try {
			for (;;) {
				int c;
				// FIXME: be efficient and read multiple at once
				c = is.read();
				if (c == -1)
					break;
				os.write(c);
			}
		} catch (IOException e) {
			e.printStackTrace(System.err);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace(System.err);
			}
			if (os != System.out && os != System.err)
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace(System.err);
				}
		}
	}
}