package org.ifcx.readit.index

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.Term
import org.apache.lucene.queryParser.QueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.NumericRangeQuery
import org.apache.lucene.search.PhraseQuery
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.similar.MoreLikeThis
import org.apache.lucene.search.spans.FieldMaskingSpanQuery
import org.apache.lucene.search.spans.SpanFirstQuery
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper
import org.apache.lucene.search.spans.SpanNearQuery
import org.apache.lucene.search.spans.SpanQuery
import org.apache.lucene.search.spans.SpanTermQuery
import org.apache.lucene.search.spans.Spans
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version

import gate.Factory

class Retriever
{
    def index_dir = new File('/mnt/LINUX_RPM/index')

//    println "index_dir = ${index_dir}"

    def indexDirectory = FSDirectory.open(index_dir)

    def reader = DirectoryReader.open(indexDirectory, true)

    def searcher = new IndexSearcher(indexDirectory)
    def analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
    def parser = new QueryParser(Version.LUCENE_CURRENT, "contents", analyzer)

    final static FILE_QUERY_ALL = '*'
    final static FILE_QUERY_ALL_TEXT = '*text'
    final static FILE_QUERY_README = 'README'
    final static FILE_QUERY_INSTALL = 'INSTALL'
    final static FILE_QUERY_SEARCH_FOR_DESCRIPTION = '?description'

    Retriever()
    {
    }

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

    List<Document> list_package_files(String package_id, boolean text_only = false)
    {
        def queryType = new TermQuery(new Term('type', 'file'))
        def queryName = new TermQuery(new Term('package.name', package_id))
        def query = new BooleanQuery()
        query.add(queryType, BooleanClause.Occur.MUST)
        query.add(queryName, BooleanClause.Occur.MUST)

        if (text_only) {
            query.add(new PrefixQuery(new Term('file.mime_type', 'text/')), BooleanClause.Occur.MUST)
        }

        def hits = searcher.search(query, 10000 /*, Sort.INDEXORDER*/)

        hits.scoreDocs.collect { hit -> searcher.doc(hit.doc) }
    }

    List<Document> list_package_files_with_name(String package_id, String file_name, boolean text_only = false)
    {
        def queryType = new TermQuery(new Term('type', 'file'))
        def queryPackageName = new TermQuery(new Term('package.name', package_id))
        def queryFileName = new TermQuery(new Term('file.name', file_name))
        def query = new BooleanQuery()
        query.add(queryType, BooleanClause.Occur.MUST)
        query.add(queryPackageName, BooleanClause.Occur.MUST)
        query.add(queryFileName, BooleanClause.Occur.MUST)

        if (text_only) {
            query.add(new PrefixQuery(new Term('file.mime_type', 'text/')), BooleanClause.Occur.MUST)
        }

        def hits = searcher.search(query, 10000 /*, Sort.INDEXORDER*/)

        hits.scoreDocs.collect { hit -> searcher.doc(hit.doc) }
    }

    def interestingTerms(String description)
    {
        def moreLikeThis = new MoreLikeThis(reader)
        moreLikeThis.minTermFreq = 0
        moreLikeThis.minDocFreq = 0

        moreLikeThis.retrieveInterestingTerms(new StringReader(description), 'contents')
    }

    List<Document> list_package_files_like_description(String package_id, String description, boolean text_only = false)
    {
        def moreLikeThis = new MoreLikeThis(reader)
        moreLikeThis.minTermFreq = 0
        moreLikeThis.minDocFreq = 0

        BooleanQuery query = new BooleanQuery()

        def queryFileType = new TermQuery(new Term('type', 'file'))
        queryFileType.boost = 0
        query.add(queryFileType, BooleanClause.Occur.MUST)

//        def queryPackageName = new TermQuery(new Term('package.name', package_id))
//        queryPackageName.boost = 0
//        query.add(queryPackageName, BooleanClause.Occur.MUST)

        if (text_only) {
            def queryMimeType = new PrefixQuery(new Term('file.mime_type', 'text/'))
            queryMimeType.boost = 0
            query.add(queryMimeType, BooleanClause.Occur.MUST)
        }

//        println (moreLikeThis.retrieveInterestingTerms(new StringReader(description), 'contents'))

        def likeDescriptionQuery = moreLikeThis.like(new StringReader(description), 'contents')
        query.add(likeDescriptionQuery, BooleanClause.Occur.SHOULD)

        def hits = searcher.search(query, 20)

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
//        String spec_file_path = package_info(package_id, 'package.spec_expanded')
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

    def parse_spec_file(spec_file_path)
    {
        try {
            def spec_text = new File(spec_file_path).text

            return RPM_SPEC.parse(spec_text)
        } catch (IOException ex) {
            ex.printStackTrace()
            return null
        }
    }

    List<Range> searchInFile(String file_path, String text, int slop)
    {
        println "Searching $file_path for '$text' with $slop slop"

        List<SpanQuery> terms = []

        TokenStream stream  = analyzer.tokenStream('contents', new StringReader(text));

        while (stream.incrementToken()) {
            terms.add(new SpanTermQuery(new Term('contents', ((CharTermAttribute) stream.getAttribute(CharTermAttribute.class)).toString())))
        }

        stream.end()
        stream.close()

//        terms.add(new FieldMaskingSpanQuery(new SpanTermQuery(new Term('file.path', file_path)), 'contents'))

        SpanNearQuery query = new SpanNearQuery(terms as SpanQuery[], slop, true)

//        SpanQuery query = new SpanMultiTermQueryWrapper()

        println query

        def spans = query.getSpans(reader)

        List<Range> ranges = []

        while (spans.next()) {
            def doc = spans.doc()

            def range = new IntRange(spans.start(), spans.end())

            ranges.add(range)

            println "$doc ${reader.document(doc).get('file.path')} ${range.from}..${range.to}"

            if (spans.isPayloadAvailable()) {
                def payload = spans.payload

                println payload
            }
        }

        println "${ranges.size()} spans found."

        ranges
    }

    PhraseQuery textToQuery(String text)
    {
        def query = new PhraseQuery()

        TokenStream stream  = analyzer.tokenStream('contents', new StringReader(text));

        while(stream.incrementToken()) {
            query.add(new Term('contents', ((CharTermAttribute) stream.getAttribute(CharTermAttribute.class)).toString()))
        }

        stream.end()
        stream.close()

        println query

        return query
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
