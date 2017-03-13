import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/mavenrepo-bundle-dependency-with-source-0.0.0-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/org.eclipse.jdt.core_3.9.1.v20130905-0837.jar")
	assert zf.getEntry("plugins/org.eclipse.jdt.core.source_3.9.1.v20130905-0837.jar")
} finally {
	zf.close();
}

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 1
