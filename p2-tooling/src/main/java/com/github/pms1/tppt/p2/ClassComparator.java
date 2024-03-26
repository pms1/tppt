package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.tycho.artifactcomparator.ArtifactDelta;
import org.eclipse.tycho.artifactcomparator.ComparatorInputStream;
import org.eclipse.tycho.zipcomparator.internal.ClassfileComparator;
import org.eclipse.tycho.zipcomparator.internal.ContentsComparator;
import org.eclipse.tycho.zipcomparator.internal.SimpleArtifactDelta;

@Component(role = FileComparator.class, hint = ClassComparator.HINT)
public class ClassComparator implements FileComparator {
	public final static String HINT = "class";

	@Requirement(role = ContentsComparator.class, hint = ClassfileComparator.TYPE)
	ContentsComparator cc;

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
