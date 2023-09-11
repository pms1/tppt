import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"r2/target/reactor-dependency-include-r2-0.0.0-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/net.sf.jopt-simple.jopt-simple_5.0.1.jar")
} finally {
	zf.close();
}
