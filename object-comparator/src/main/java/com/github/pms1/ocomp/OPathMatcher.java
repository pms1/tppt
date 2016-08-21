package com.github.pms1.ocomp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.pms1.ocomp.ObjectComparator.OPath2;
import com.google.common.base.Preconditions;

public class OPathMatcher {

	private enum ParseState {
		START, IN_NAME, INDEX_START, ANY_INDEX_EXPECT_CLOSE_BRACKED, IN_MATCH_INDEX
	}

	private interface SegmentMatcher {
		boolean match(String segment);
	}

	private final List<SegmentMatcher> segmentMatchers;

	private OPathMatcher(List<SegmentMatcher> segmentMatchers) {
		this.segmentMatchers = segmentMatchers;
	}

	private static class NameMatcher implements SegmentMatcher {
		final String expected;

		NameMatcher(String expected) {
			Preconditions.checkNotNull(expected);
			Preconditions.checkArgument(!expected.isEmpty());
			this.expected = expected;
		}

		@Override
		public boolean match(String segment) {
			return segment.equals(expected);
		}

	}

	private static class AnyIndexMatcher implements SegmentMatcher {

		@Override
		public boolean match(String segment) {
			return segment.startsWith("[") && segment.endsWith("]");
		}

	}

	private static class IndexMatcher extends NameMatcher {

		IndexMatcher(String s) {
			super("[" + s + "]");
		}

	}

	public static OPathMatcher create(String string) {

		ParseState state = ParseState.START;

		String current = null;

		List<SegmentMatcher> fragments = new ArrayList<>(5);

		for (char c : string.toCharArray()) {
			switch (state) {
			case START:
				if (c == '/') {
					state = ParseState.IN_NAME;
					current = "" + c;
				} else {
					throw new IllegalArgumentException();
				}
				break;
			case IN_NAME:
				if (c == '/') {
					fragments.add(new NameMatcher(current));
					current = "" + c;
				} else if (c == '[') {
					fragments.add(new NameMatcher(current));
					current = null;
					state = ParseState.INDEX_START;
				} else {
					current += c;
				}
				break;
			case INDEX_START:
				if (c == '*') {
					if (current != null)
						throw new IllegalArgumentException();
					state = ParseState.ANY_INDEX_EXPECT_CLOSE_BRACKED;
				} else {
					if (current != null)
						throw new IllegalArgumentException();
					current = "" + c;
					state = ParseState.IN_MATCH_INDEX;
				}
				break;
			case IN_MATCH_INDEX:
				if (c == ']') {
					fragments.add(new IndexMatcher(current));
					current = null;
					state = ParseState.START;
				} else {
					current += c;
				}
				break;
			case ANY_INDEX_EXPECT_CLOSE_BRACKED:
				if (c == ']') {
					fragments.add(new AnyIndexMatcher());
					state = ParseState.START;
				} else {
					throw new IllegalArgumentException();
				}
				break;
			}
		}

		switch (state) {
		case IN_NAME:
			fragments.add(new NameMatcher(current));
			break;
		case START:
			break;
		default:
			throw new IllegalArgumentException();
		}

		return new OPathMatcher(Collections.unmodifiableList(fragments));
	}

	public boolean matches(OPath2 path) {

		if (path.size() != segmentMatchers.size())
			return false;

		for (int i = 0; i != path.size(); ++i) {
			if (!segmentMatchers.get(i).match(path.subPath(i, i + 1).getPath()))
				return false;
		}

		return true;
	}

}
