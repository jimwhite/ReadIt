#!/usr/bin/env groovy
// Can't call this script 'query' because we use a variable with that name (or vice versa).

// CLASSPATH=src:/NLP_TOOLS/info_retrieval/lucene/lucene-3.5.0/lucene-core-3.5.0.jar ./query.groovy --entity_ref E0175811
// CLASSPATH=src:/NLP_TOOLS/info_retrieval/lucene/lucene-3.5.0/lucene-core-3.5.0.jar ./query.groovy --entity_id E0662857 entity_id:E0662857
// CLASSPATH=src:/NLP_TOOLS/info_retrieval/lucene/lucene-3.5.0/lucene-core-3.5.0.jar ./query.groovy --entity_class 'Infobox Philosopher' --entity_class Infobox_Philosopher

import org.apache.lucene.store.FSDirectory
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.queryParser.QueryParser
import org.apache.lucene.util.Version
import org.apache.lucene.search.TermQuery
import org.apache.lucene.index.Term
import org.apache.lucene.search.Query
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BooleanClause

args = args as List

index_dir = new File('/mnt/LINUX_RPM/index')

if (args && args[0] == '--index') {
    args.remove(0)
    index_dir = new File(args.remove(0))
}

println "index_dir = ${index_dir}"

indexDirectory = FSDirectory.open(index_dir)

searcher = new IndexSearcher(indexDirectory)
analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
parser = new QueryParser(Version.LUCENE_CURRENT, "contents", analyzer)

query = new BooleanQuery()

while (args && (args.head().startsWith('--'))) {
    def field = args.remove(0).substring(2)
    def value = args.remove(0)

    def occur = BooleanClause.Occur.MUST
    if (field.startsWith('-')) {
        field = field.substring(1)
        occur = BooleanClause.Occur.MUST_NOT
    }

    query.add(field == 'subject' ? parser.getFieldQuery('subject', value, true) : new TermQuery(new Term(field, value)), occur)
}

if (args) {
    query.add(parser.parse(args.join(' ')), BooleanClause.Occur.MUST)
}

do_query(query)

def do_query(Query query)
{
    println query

    hits = searcher.search(query, 200)

    println "${hits.totalHits} hits"

    for (hit in hits.scoreDocs) {
        println "-----"

        def doc = searcher.doc(hit.doc)
//        def path = doc.get("file.path")
//        if (path) println(path)
        doc.fields.each { println "${it.name()}:${it.stringValue()}" }
        println()
    }
}
