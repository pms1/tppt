import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/test-0.0.1-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/org.glassfish.jersey.core.jersey-client_2.23.1.jar")
	assert !zf.getEntry("plugins/org.glassfish.jersey.core.jersey-common_2.23.1.jar")
	assert zf.getEntry("plugins/org.glassfish.hk2.osgi-resource-locator_1.0.1.jar")
} finally {
	zf.close();
}
	