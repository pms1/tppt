#! /bin/bash

rulesURI=file:$(cygpath -m -a releng/rules.xml)
mvn versions:display-dependency-updates versions:display-plugin-updates -Dmaven.version.rules=$rulesURI
