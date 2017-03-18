def l = new File(basedir, 'target/repository').list();
Arrays.sort(l);
assert l == [ 'artifacts.jar', 'content.jar', 'p2.index' ]
