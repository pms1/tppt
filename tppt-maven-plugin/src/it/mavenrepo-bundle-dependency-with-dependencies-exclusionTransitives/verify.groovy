import java.util.zip.ZipFile

def zf = new ZipFile(new File(basedir,"target/mavenrepo-bundle-dependency-with-dependencies-exclusionTransitives-0.0.0-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/org.glassfish.jersey.core.jersey-client_2.23.1.jar")
	assert zf.getEntry("plugins/org.glassfish.jersey.core.jersey-common_2.23.1.jar")
	assert !zf.getEntry("plugins/org.glassfish.hk2.osgi-resource-locator_1.0.1.jar")
} finally {
	zf.close();
}

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 1
