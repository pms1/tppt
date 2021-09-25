package com.github.pms1.tppt.mirror;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map.Entry;
import java.util.SortedMap;

public final class Uris {
	private Uris() {

	}

	public static boolean isChild(URI parent, URI child) {
		if (!isDirectory(parent))
			throw new IllegalArgumentException("Require directory URI: parent=" + parent);
		return !parent.relativize(child).isAbsolute();
	}

	public static boolean isDirectory(URI uri) {
		return uri.getPath().endsWith("/");
	}

	public static URI reparent(URI uri, URI oldParent, URI newParent) {
		if (!isDirectory(oldParent))
			throw new IllegalArgumentException("Require directory URI: oldParent=" + oldParent);
		if (!isDirectory(newParent))
			throw new IllegalArgumentException("Require directory URI: newParent=" + newParent);
		if (!isChild(oldParent, uri))
			throw new IllegalArgumentException();

		return newParent.resolve(oldParent.relativize(uri));
	}

	public static <T> Entry<URI, T> findLongestPrefix(SortedMap<URI, T> map, URI prefix) {
		Entry<URI, T> solution = null;

		for (Entry<URI, T> e : map.entrySet()) {
			// if the current entry is bigger than what we search, we can stop
			if (e.getKey().compareTo(prefix) > 0)
				break;

			if (isChild(e.getKey(), prefix))
				solution = e;
		}

		return solution;
	}

	public static URI normalizeDirectory(URI base) {
		if (base.getPath().endsWith("/"))
			return base;

		try {
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), base.getPath() + "/",
					base.getQuery(), base.getFragment());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

	}
}
