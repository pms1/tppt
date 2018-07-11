package com.github.pms1.tppt.mirror;

import java.net.URI;

public final class Uris {
	private Uris() {

	}

	static boolean isChild(URI parent, URI uri2) {
		return !parent.relativize(uri2).isAbsolute();
	}

	static URI reparent(URI uri, URI oldParent, URI newParent) {
		if (!isChild(oldParent, uri))
			throw new IllegalArgumentException();
		return newParent.resolve(oldParent.relativize(uri));
	}
}
