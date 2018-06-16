# Target platform provisioning tools for p2 repositories

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.pms1.tppt/tppt-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.pms1.tppt/tppt-maven-plugin)
[![Build Status](https://travis-ci.org/pms1/tppt.svg?branch=master)](https://travis-ci.org/pms1/tppt)
[![Coverage Status](https://coveralls.io/repos/github/pms1/tppt/badge.svg?branch=)](https://coveralls.io/github/pms1/tppt?branch=)

An Apache Maven plugin for creating p2 repositories along with features directly from Maven artifacts. This
provides easy use of those artifacts during development of Eclipse RCP applications within the Eclipse IDE.

This project was created when I wanted to use [Jersey](https://jersey.github.io/) inside an Eclipse RCP application and found it incredibly inconvenient to transform it's Maven artifacts into a p2 repository directly usable from Eclipse with the various existing tools.

## Features 

* Create a p2 repository based on Maven's project dependency

  * Supports dependency from the same reactor or repositories
 
  * Detects source artifacts and transforms them into Eclipse source bundles

  * Converts dependencies to OSGi bundles using [bnd](http://bnd.bndtools.org/) on the fly

  * Creates a feature with all dependencies
 
  * Creates a category for easier use in Eclipse's .target editor

* Deploy p2 repositories to a file system structure that can be read by eclipse or exposed via http using a webserver

* Compare created repositories to results from a previous Maven build and discard the changes if the two repositories are considered _equal_

  * Ignores changes if only the version numbers or the build qualifier is really changed
 
  * Allows subsequent Maven builds to produce a binary identical result
 
* Create composite p2 repositories from multiple "normal" p2 repositories in the same reactor

* Add mirrored artifacts from other p2 repositories

  * Uses a transparent HTTP-level cache that will avoid downloading the same artifacts multiple times
  * Automatically mirrors source bundles / features if they follow the usual naming conventions
  * [Tycho](https://eclipse.org/tycho/)'s [mirror goal](https://wiki.eclipse.org/Tycho/Additional_Tools#mirror_goal) can be used as well


## Usage

To create a P2 repository from a Maven artifact that is already an OSGi bundle is as simple as:

```xml
 	<groupId>com.github.pms1.tppt</groupId>
	<artifactId>mavenrepo-bundle-dependency</artifactId>
	<version>0.0.0-SNAPSHOT</version>
	<packaging>tppt-repository</packaging>

	<dependencies>
		<dependency>
			<groupId>net.sf.jopt-simple</groupId>
			<artifactId>jopt-simple</artifactId>
			<version>5.0.1</version>
		</dependency>
	</dependencies>
	
	<build>
		<plugins>
			<plugin>
				<groupId>com.github.pms1.tppt</groupId>
				<artifactId>tppt-maven-plugin</artifactId>
				<version>0.1.0</version>
				<extensions>true</extensions>
			</plugin>
		</plugins>
	</build>
```

This will create the following p2 repository:

```
artifacts.jar
content.jar
features/
features/com.github.pms1.tppt.mavenrepo-bundle-dependency_0.0.0.100.jar
p2.index
plugins/
plugins/net.sf.jopt-simple.jopt-simple.source_5.0.1.jar
plugins/net.sf.jopt-simple.jopt-simple_5.0.1.jar
```
