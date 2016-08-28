import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/test-0.0.1-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/net.sf.jopt-simple.jopt-simple_5.0.1.jar")
	assert zf.getEntry("plugins/net.sf.jopt-simple.jopt-simple.source_5.0.1.jar")
} finally {
	zf.close();
}
