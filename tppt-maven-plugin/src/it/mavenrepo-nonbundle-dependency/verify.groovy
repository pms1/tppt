import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/test-0.0.1-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/antlr_2.7.7.jar")
} finally {
	zf.close();
}
	