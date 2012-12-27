#!/bin/bash
CLASSPATH=$(printf "%s:" lib/*)src JAVA_OPTS="-Xmx1400M -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=25" groovy src/org/ifcx/readit/index/Explorer.groovy
