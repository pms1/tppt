import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/mavenrepo-bundle-repackaged-keepname-0.0.0-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/com.google.guava_33.1.0.100.jar")
} finally {
	zf.close();
}

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 1
