import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/shortVersion-1.0.zip"));
try {
	assert zf.getEntry("features/com.github.pms1.tppt.shortVersion_1.0.0.100.jar")
} finally {
	zf.close();
}

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 1
	