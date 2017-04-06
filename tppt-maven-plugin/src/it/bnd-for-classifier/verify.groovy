import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/bnd-for-classifier-0.0.0-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/bar_3.0.0.jar")
} finally {
	zf.close();
}

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 1
