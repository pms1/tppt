package com.github.pms1.tppt.mirror;

import java.net.URI;
import java.util.Map.Entry;
import java.util.SortedMap;

public final class Uris {
	private Uris() {

	}

	public static boolean isChild(URI parent, URI uri2) {
		return !parent.relativize(uri2).isAbsolute();
	}

	public static URI reparent(URI uri, URI oldParent, URI newParent) {
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
}
