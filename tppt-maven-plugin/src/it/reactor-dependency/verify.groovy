
def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains(' on project reactor-dependency-r2: Repository dependency to com.github.pms1.tppt:reactor-dependency-r1:tppt-repository:0.0.0-SNAPSHOT ') }
