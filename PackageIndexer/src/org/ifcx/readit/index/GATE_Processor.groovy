package org.ifcx.readit.index

import gate.Corpus
import gate.CorpusController
import gate.Document
import gate.Factory
import gate.Gate
import gate.util.persistence.PersistenceManager

class GATE_Processor
{

    Corpus corpus

    List<String> document_names = []

    Corpus tempCorpus

    CorpusController application

    GATE_Processor()
    {
        Gate.init()

        corpus = (Corpus) Factory.createResource("gate.corpora.CorpusImpl")
        tempCorpus = (Corpus) Factory.createResource("gate.corpora.CorpusImpl")

        // load the saved application
        application = (CorpusController)PersistenceManager.loadObjectFromFile(new File("tokenizer.xgapp"))

        application.corpus = tempCorpus
    }

    String taggedHTMLforFile(String file_path)
    {
        def uri = new File(file_path).toURI()

        def docIndex = document_names.indexOf(uri.toString())

        Document document = null

        if (docIndex >= 0) document = corpus.get(docIndex)

        if (!document) {
            println "Processing $file_path"
            document = processFile(uri)
            corpus.add(document)
            document_names = corpus.getDocumentNames()
        }

        def docAnnotationSet = document.getAnnotations()
//        def annotations = docAnnotationSet.get(["Token", "Sentence"] as Set<String>)
        def xml = document.toXml(docAnnotationSet)

        return xml
    }

    Document processFile(URI uri)
    {
        def params = Factory.newFeatureMap()
        params.put("sourceUrl", uri.toURL())
        params.put("preserveOriginalContent", true)
//        params.put("collectRepositioningInfo", true)

        Document doc = (Document) Factory.createResource("gate.corpora.DocumentImpl", params)

        tempCorpus.add(doc)

        application.execute()

        tempCorpus.clear()

        doc.name = uri.toString()
        return doc
    }



}
