#! /bin/bash

mvn -Prelease deploy -s .travis/settings.xml -DskipTests=true -Dinvoker.skip=true -B
