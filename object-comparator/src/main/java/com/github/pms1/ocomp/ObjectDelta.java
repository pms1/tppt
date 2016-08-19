package com.github.pms1.ocomp;

import com.github.pms1.ocomp.ObjectComparator.ChangeType;
import com.google.common.base.Preconditions;

public class ObjectDelta {
	private final String path;
	private final ChangeType change;
	private final Object left;
	private final Object right;

	public ObjectDelta(String path, ChangeType change, Object left, Object right) {
		Preconditions.checkNotNull(path);
		Preconditions.checkArgument(!path.isEmpty());
		this.path = path;
		Preconditions.checkNotNull(change);
		this.change = change;
		this.left = left;
		this.right = right;
	}

	@Override
	public String toString() {
		return path + ": " + change + " " + left + " <> " + right;
	}

	public String getPath() {
		return path;
	}
}
