import java.util.zip.ZipFile
import java.nio.file.Path
import java.nio.file.Files

def zf = new ZipFile(new File(basedir,"target/mirror-duplicate-artifacts-32-0.0.0-SNAPSHOT.zip"));
try {
	assert zf.getEntry("plugins/org.eclipse.emf.codegen_2.10.0.v20140901-1055.jar")
	assert zf.getEntry("features/org.eclipse.emf.codegen_2.10.0.v20140901-1055.jar")
} finally {
	zf.close();
}

def f = new File(basedir ,'../../deploymentTarget')

def d = f.list().grep { it.startsWith("mirror-duplicate-artifacts-32-0.0.0-SNAPSHOT-") }
assert d.size() == 1
def r = f.toPath().resolve(d[0])
assert Files.isDirectory(r)

assert Files.exists(r.resolve("plugins/org.eclipse.emf.codegen_2.10.0.v20140901-1055.jar"))
assert Files.exists(r.resolve("features/org.eclipse.emf.codegen_2.10.0.v20140901-1055.jar"))

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 1
