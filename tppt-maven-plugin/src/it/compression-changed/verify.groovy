def l = new File(basedir, 'target/repository').list();
Arrays.sort(l);
assert l == [ 'artifacts.xml', 'content.xml', 'p2.index' ]

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 2
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 0
