import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/mirror-0.0.0-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/net.sf.jopt-simple.jopt-simple_5.0.1.jar")
	assert zf.getEntry("plugins/org.eclipse.osgi_3.12.50.v20170928-1321.jar")
	assert !zf.getEntry("plugins/org.eclipse.osgi_3.12.1.v20170821-1548.jar")
} finally {
	zf.close();
}

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 1
