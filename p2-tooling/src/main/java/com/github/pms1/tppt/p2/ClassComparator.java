package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.tycho.artifactcomparator.ArtifactDelta;
import org.eclipse.tycho.artifactcomparator.ComparatorInputStream;
import org.eclipse.tycho.zipcomparator.internal.ClassfileComparator;
import org.eclipse.tycho.zipcomparator.internal.ContentsComparator;
import org.eclipse.tycho.zipcomparator.internal.SimpleArtifactDelta;

@Named(ClassComparator.HINT)
@Singleton
public class ClassComparator implements FileComparator {
	public final static String HINT = "class";

	@Inject
	@Named(ClassfileComparator.TYPE)
	private ContentsComparator cc;

	@Override
	public void compare(FileId file1, Path p1, FileId file2, Path p2, Consumer<FileDelta> dest) throws IOException {

		try (ComparatorInputStream is1 = new ComparatorInputStream(Files.newInputStream(p1))) {
			try (ComparatorInputStream is2 = new ComparatorInputStream(Files.newInputStream(p2))) {
				ArtifactDelta delta = cc.getDelta(is1, is2, null);
				if (delta != null)
					dest.accept(new FileDelta(file1, file2, "Code changed"));

				if (false)
					if (delta instanceof SimpleArtifactDelta) {
						SimpleArtifactDelta sad = (SimpleArtifactDelta) delta;
						System.err.println("--baseline--\n" + sad.getBaseline() + "\n");
						System.err.println("--reactor--\n" + sad.getReactor() + "\n");
					}
			}
		}
	}

}
