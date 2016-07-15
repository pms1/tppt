import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/test-0.0.1-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/org.eclipse.jdt.core_3.9.1.v20130905-0837.jar")
	assert zf.getEntry("plugins/org.eclipse.jdt.core.source_3.9.1.v20130905-0837.jar")
} finally {
	zf.close();
}
	