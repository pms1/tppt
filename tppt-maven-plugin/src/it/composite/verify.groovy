import java.util.zip.ZipFile

def l = new File(basedir, 'rcomp/target/repository').list();
Arrays.sort(l);
assert l == [
	'compositeArtifacts.jar',
	'compositeContent.jar',
	'p2.index'
]

def text
def zf;

text = null
zf = new ZipFile(new File(basedir,"r1/target/repository/artifacts.jar"));
zf.entries().each {
	switch(it.name) {
		case "artifacts.xml":
			text=new XmlSlurper().parseText(zf.getInputStream(it).text);
	}
}
assert text.'@name' == 'Project R1'

text = null
zf = new ZipFile(new File(basedir,"r1/target/repository/content.jar"));
zf.entries().each {
	switch(it.name) {
		case "content.xml":
			text=new XmlSlurper().parseText(zf.getInputStream(it).text);
	}
}
assert text.'@name' == 'Project R1'


text = null
zf = new ZipFile(new File(basedir,"rcomp/target/repository/compositeArtifacts.jar"));
zf.entries().each {
	switch(it.name) {
		case "compositeArtifacts.xml":
			text=new XmlSlurper().parseText(zf.getInputStream(it).text);
	}
}
assert text.'@name' == 'Project Composite'

text = null
zf = new ZipFile(new File(basedir,"rcomp/target/repository/compositeContent.jar"));
zf.entries().each {
	switch(it.name) {
		case "compositeContent.xml":
			text=new XmlSlurper().parseText(zf.getInputStream(it).text);
	}
}
assert text.'@name' == 'Project Composite'

def f = new File(basedir ,'../../deploymentTarget');

assert new HashSet(f.list().grep { it.startsWith("r1-") || it.startsWith("r2-") || it.startsWith("rcomp-") }.collect { it -> it.substring(it.lastIndexOf('-') + 1) }).size() == 1

def buildLog = new File(basedir, 'build.log').readLines();

assert buildLog.grep { it.contains('Deploying to ') }.size() == 6
assert buildLog.grep { it.contains(' Equal to existing repository, skipping deployment') }.size() == 3
