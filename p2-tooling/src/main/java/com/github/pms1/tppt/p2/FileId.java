package com.github.pms1.tppt.p2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Joiner;

public class FileId {
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((root == null) ? 0 : root.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileId other = (FileId) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (root == null) {
			if (other.root != null)
				return false;
		} else if (!root.equals(other.root))
			return false;
		return true;
	}

	private final Path root;
	private final List<String> path;

	private FileId(Path p, List<String> path) {
		this.root = p;
		this.path = path;
	}

	public static FileId newRoot(Path p) {
		return new FileId(p, Collections.emptyList());
	}

	public static FileId newChild(FileId parent, Path p) {
		List<String> np = new ArrayList<String>(parent.path);
		np.add(p.toString());
		return new FileId(parent.root, Collections.unmodifiableList(np));
	}

	@Override
	public String toString() {
		if (path.isEmpty())
			return root.toString();
		else
			return root + " " + Joiner.on(" ").join(path);
	}

	public static void main(String[] args) {
		// jar:<url>!/{entry}
	}

	public FileId getParent() {
		List<String> np = new ArrayList<String>(path);
		np.remove(path.size() - 1);
		return new FileId(root, Collections.unmodifiableList(np));
	}
}
