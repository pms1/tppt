import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/mirror-0.0.0-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/net.sf.jopt-simple.jopt-simple_5.0.1.jar")
	assert zf.getEntry("plugins/org.eclipse.osgi_3.11.2.v20161107-1947.jar")
} finally {
	zf.close();
}

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 1
