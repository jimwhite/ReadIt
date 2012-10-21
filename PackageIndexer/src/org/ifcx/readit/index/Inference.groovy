package org.ifcx.readit.index

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

/**
 * Created by IntelliJ IDEA.
 * User: TLabarre
 * Date: 20/05/12
 * Time: 22:25
 * To change this template use File | Settings | File Templates.
 */

class Inference
{

    FSDirectory indexDirectory = FSDirectory.open(new File("data/cities/index"))

    IndexSearcher searcher = new IndexSearcher(indexDirectory)

    Set<Answer> expand_city(Answer answer, Slot state_or_province_slot, Slot country_slot)
    {
        Set<Answer> answers = new HashSet<Answer>()

        String city = answer.value.toLowerCase()
        def query_country = new BooleanQuery()

        query_country.add(new TermQuery(new Term('country', city)), BooleanClause.Occur.MUST)

        // If the city matches a country, we remove it
        def hits_country = searcher.search(query_country, 200)

        if (hits_country.totalHits==0) {
            def query = new BooleanQuery()
            query.add(new TermQuery(new Term('city', city)), BooleanClause.Occur.MUST)

            def hits = searcher.search(query, 200)

            if (hits.totalHits > 0)    {
                Long max_population = 0
                def chosen_doc = null

                for (hit in hits.scoreDocs) {
                    def doc = searcher.doc(hit.doc)
                    if (Long.valueOf(doc.get("population")) >= max_population) {
                        chosen_doc = doc
                        max_population = Long.valueOf(doc.get("population"))
                    }
                }

                answers.add(new Answer(slot:state_or_province_slot, doc_id:answer.doc_id, stringValue: chosen_doc.get('state_or_province')))
                answers.add(new Answer(slot:country_slot, doc_id:answer.doc_id, stringValue: chosen_doc.get('country')))
            }
        }

        answers
    }
}