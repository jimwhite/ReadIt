package org.ifcx.readit.index

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.Term
import org.apache.lucene.queryParser.QueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.NumericRangeQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version

class Retriever
{
    def index_dir = new File('/mnt/LINUX_RPM/index')

//    println "index_dir = ${index_dir}"

    def indexDirectory = FSDirectory.open(index_dir)

    def searcher = new IndexSearcher(indexDirectory)
    def analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
    def parser = new QueryParser(Version.LUCENE_CURRENT, "contents", analyzer)

    List<String> list_packages()
    {
        def query = new TermQuery(new Term('type', 'package'))

        def hits = searcher.search(query, 10000 /*, Sort.INDEXORDER*/)

        hits.scoreDocs.collect { hit ->
            def doc = searcher.doc(hit.doc)
            doc['package.name']
        }
    }

    List<Document> list_readmes(String file_name)
    {
        def queryType = new TermQuery(new Term('type', 'file'))
        def queryName = new TermQuery(new Term('file.name', file_name))
        def querySize = NumericRangeQuery.newLongRange('file.size', 0, 0, true, true)
//        def querySize = NumericRangeQuery.newLongRange('file.size', 1, NumericRangeQuery.LONG_POSITIVE_INFINITY, true, true)
        def query = new BooleanQuery()
        query.add(queryType, BooleanClause.Occur.MUST)
        query.add(queryName, BooleanClause.Occur.MUST)
        query.add(querySize, BooleanClause.Occur.MUST_NOT)

        def hits = searcher.search(query, 10000 /*, Sort.INDEXORDER*/)

        hits.scoreDocs.collect { hit -> searcher.doc(hit.doc) }
    }

    List<Document> list_package_files(String package_id)
    {
        def queryType = new TermQuery(new Term('type', 'file'))
        def queryName = new TermQuery(new Term('package.name', package_id))
        def query = new BooleanQuery()
        query.add(queryType, BooleanClause.Occur.MUST)
        query.add(queryName, BooleanClause.Occur.MUST)

        def hits = searcher.search(query, 10000 /*, Sort.INDEXORDER*/)

        hits.scoreDocs.collect { hit -> searcher.doc(hit.doc) }
    }

    Document package_doc(String package_id)
    {
        def queryType = new TermQuery(new Term('type', 'package'))
        def queryName = new TermQuery(new Term('package.name', package_id))
        def query = new BooleanQuery()
        query.add(queryType, BooleanClause.Occur.MUST)
        query.add(queryName, BooleanClause.Occur.MUST)

        def hits = searcher.search(query, 3)

        switch (hits.totalHits) {
            case 0:
                return null
            case 1:
                return searcher.doc(hits.scoreDocs[0].doc)
            default:
                println "More than one hit in package_doc for $package_id"
                return null
        }
    }

    def package_info(String package_id, String field)
    {
        def queryType = new TermQuery(new Term('type', 'package'))
        def queryName = new TermQuery(new Term('package.name', package_id))
        def query = new BooleanQuery()
        query.add(queryType, BooleanClause.Occur.MUST)
        query.add(queryName, BooleanClause.Occur.MUST)

        def hits = searcher.search(query, 3)

        switch (hits.totalHits) {
            case 0:
                return null
            case 1:
                return searcher.doc(hits.scoreDocs[0].doc).get(field)
            default:
                println "More than one hit in package_info for $package_id"
                return null
        }
    }

    def spec_for_package(String package_id)
    {
        String spec_file_path = package_info(package_id, 'package.spec')

        if (!spec_file_path) return null

        try {
            def spec_text = new File(spec_file_path).text

            return RPM_SPEC.parse(spec_text)
        } catch (IOException ex) {
            ex.printStackTrace()
            return null
        }
    }

//    def list(Map<String, String> terms)
//    {
//        def query = new BooleanQuery()
//        while (args && (args.head().startsWith('--'))) {
//            def field = args.remove(0).substring(2)
//            def value = args.remove(0)
//
//            def occur = BooleanClause.Occur.MUST
//            if (field.startsWith('-')) {
//                field = field.substring(1)
//                occur = BooleanClause.Occur.MUST_NOT
//            }
//
//            query.add(field == 'subject' ? parser.getFieldQuery('subject', value, true) : new TermQuery(new Term(field, value)), occur)
//            if (args) {
//                query.add(parser.parse(args.join(' ')), BooleanClause.Occur.MUST)
//            }
//
//            do_query(query)
//
//        }
//
//    def do_query(Query query)
//    {
//        println query
//
//        def hits = searcher.search(query, 200)
//
//        println "${hits.totalHits} hits"
//
//        for (hit in hits.scoreDocs) {
//            println "-----"
//
//            def doc = searcher.doc(hit.doc)
//            doc.fields.each { println "${it.name()}:${it.stringValue()}" }
//            println()
//        }
//    }

}
