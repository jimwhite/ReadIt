#!/bin/sh

# This Bash script searches through the given Lucene Index to retrieve documents matching a given query

if [ -z "$LUCENE_HOME" ] ; then
   LUCENE_HOME=/NLP_TOOLS/info_retrieval/lucene/lucene-3.5.0
fi

if [ -z "$GROOVY_HOME" ] ; then
   GROOVY_HOME=`pwd`/groovy-1.8.6
   PATH=$GROOVY_HOME/bin:$PATH
fi

export CLASSPATH=src:$LUCENE_HOME/lucene-core-3.5.0.jar

./Inference.groovy $*