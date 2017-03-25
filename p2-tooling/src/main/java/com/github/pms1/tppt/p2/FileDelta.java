package com.github.pms1.tppt.p2;

import java.util.Arrays;

import com.google.common.base.Preconditions;

public class FileDelta {
	private final FileId f1;
	private final FileId f2;
	private final String description;
	private final Object[] parameters;

	public FileDelta(FileId f1, FileId f2, String description, Object... parameters) {
		Preconditions.checkNotNull(f1);
		this.f1 = f1;
		Preconditions.checkNotNull(f2);
		this.f2 = f2;
		Preconditions.checkNotNull(description);
		Preconditions.checkArgument(!description.isEmpty());
		this.description = description;
		this.parameters = Preconditions.checkNotNull(parameters);
	}

	public FileId getBaselineFile() {
		return f1;
	}

	public FileId getCurrentFile() {
		return f2;
	}

	@Override
	public String toString() {
		return f1 + " -> " + f2 + ": " + description + " " + Arrays.toString(parameters);
	}

	public String getDescription() {
		return description;
	}

	public Object[] getParameters() {
		return parameters;
	}

	@Override
	final public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((f1 == null) ? 0 : f1.hashCode());
		result = prime * result + ((f2 == null) ? 0 : f2.hashCode());
		result = prime * result + Arrays.hashCode(parameters);
		return result;
	}

	@Override
	final public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileDelta other = (FileDelta) obj;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (f1 == null) {
			if (other.f1 != null)
				return false;
		} else if (!f1.equals(other.f1))
			return false;
		if (f2 == null) {
			if (other.f2 != null)
				return false;
		} else if (!f2.equals(other.f2))
			return false;
		if (!Arrays.equals(parameters, other.parameters))
			return false;
		return true;
	}
}
