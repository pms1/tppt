def l = new File(basedir, 'rcomp/target/repository').list();
Arrays.sort(l);
assert l == [
	'compositeArtifacts.jar',
	'compositeContent.jar',
	'p2.index'
]

def f = new File(basedir ,'../../deploymentTarget');

assert new HashSet(f.list().grep { it.startsWith("r1-") || it.startsWith("r2-") || it.startsWith("rcomp-") }.collect { it -> it.substring(it.lastIndexOf('-') + 1) }).size() == 1

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 6
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 3
