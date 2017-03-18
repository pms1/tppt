def l = new File(basedir, 'rcomp/target/repository').list();
Arrays.sort(l);
assert l == [ 'compositeArtifacts.jar', 'compositeContent.jar', 'p2.index' ]

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 6
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 3
