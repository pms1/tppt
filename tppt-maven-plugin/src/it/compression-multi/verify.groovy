def l = new File(basedir, 'target/repository').list();
Arrays.sort(l);
assert l == [ 'artifacts.jar', 'artifacts.xml', 'content.jar', 'content.xml', 'p2.index' ]
