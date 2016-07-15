import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"repository/target/test-0.0.1-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/my-osgi-bundles.examplebundle_0.0.1.SNAPSHOT.jar")
	assert zf.getEntry("plugins/org.osgi.core_4.0.0.jar")
} finally {
	zf.close();
}
	