package com.github.pms1.tppt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InterpolatedString {
	private enum State {
		DEFAULT, QUOTED_DEFAULT, SEEN_AT, VARIABLE, QUOTED_VARIABLE;
	}

	private interface Part {

	}

	static private class Text implements Part {
		String text;

		Text(String text) {
			this.text = text;
		}
	}

	static private class Variable implements Part {
		List<String> var;

		public Variable(List<String> var) {
			this.var = var;
		}

	}

	private List<Part> parts;

	private InterpolatedString(List<Part> parts) {
		this.parts = parts;
	}

	public static InterpolatedString parse(String text) {

		State s = State.DEFAULT;
		List<Part> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		List<String> var = null;

		for (char c : text.toCharArray()) {
			switch (s) {
			case DEFAULT:
				switch (c) {
				case '\\':
					s = State.QUOTED_DEFAULT;
					break;
				case '@':
					s = State.SEEN_AT;
					if (current.length() != 0)
						result.add(new Text(current.toString()));
					current = null;
					break;
				default:
					current.append(c);
					break;
				}
				break;
			case QUOTED_DEFAULT:
				s = State.DEFAULT;
				current.append(c);
				break;
			case QUOTED_VARIABLE:
				s = State.VARIABLE;
				current.append(c);
				break;
			case SEEN_AT:
				switch (c) {
				case '{':
					s = State.VARIABLE;
					current = new StringBuilder();
					var = new ArrayList<>();
					break;
				default:
					throw new IllegalArgumentException();
				}
				break;
			case VARIABLE:
				switch (c) {
				case '}':
					s = State.DEFAULT;
					var.add(current.toString());
					result.add(new Variable(Collections.unmodifiableList(var)));
					if (var.size() == 1 && current.length() == 0)
						throw new IllegalArgumentException();
					current = new StringBuilder();
					break;
				case '\\':
					s = State.QUOTED_VARIABLE;
					break;
				case ':':
					var.add(current.toString());
					current = new StringBuilder();
					break;
				default:
					current.append(c);
					break;
				}
				break;
			default:
				throw new IllegalStateException();
			}
		}
		if (current.length() != 0)
			result.add(new Text(current.toString()));
		if (s != State.DEFAULT)
			throw new IllegalArgumentException();
		return new InterpolatedString(result);
	}

	public interface Visitor {
		void visitText(String text);

		void visitVariable(List<String> variable);
	}

	public void accept(Visitor visitor) {
		for (Part p : parts) {
			if (p instanceof Text)
				visitor.visitText(((Text) p).text);
			else
				visitor.visitVariable(((Variable) p).var);
		}
	}
}
