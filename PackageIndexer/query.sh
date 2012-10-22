#!/bin/sh
if [ -z "$LUCENE_HOME" ] ; then
   LUCENE_HOME=/Users/jim/Projects/Apache/Lucene/lucene-3.5.0
fi

if [ -z "$GROOVY_HOME" ] ; then
   GROOVY_HOME=`pwd`/groovy-1.8.6
   PATH=$GROOVY_HOME/bin:$PATH
fi

export CLASSPATH=src:$LUCENE_HOME/lucene-core-3.5.0.jar

# Examples:
# ./query.sh --entity_ref E0175811
# ./query.sh --entity_id E0662857
# ./query.sh --entity_class 'Infobox Philosopher' --entity_class Infobox_Philosopher

./query_do.groovy $*
