package scripts

import gate.Corpus
import gate.CorpusController
import gate.Factory
import gate.Gate
import gate.Utils
import gate.compound.CompoundDocument
import gate.util.persistence.PersistenceManager
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory

import static gate.Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME
import static gate.Document.DOCUMENT_ENCODING_PARAMETER_NAME
import static gate.Document.DOCUMENT_MARKUP_AWARE_PARAMETER_NAME
import static gate.Document.DOCUMENT_PRESERVE_CONTENT_PARAMETER_NAME
import static gate.Document.DOCUMENT_REPOSITIONING_PARAMETER_NAME
import static gate.SimpleDocument.DOCUMENT_URL_PARAMETER_NAME

Gate.init()

Corpus corpus = (Corpus) Factory.createResource("gate.corpora.CorpusImpl")

// load the saved application - we don't use it here, this is just to get the Alignment plugin loaded.
application = (CorpusController)PersistenceManager.loadObjectFromFile(new File("tokenizer.xgapp"))

LINUX_RPM = new File("/mnt/LINUX_RPM")

packages_dir = new File(LINUX_RPM, "packages")

index_dir = new File(LINUX_RPM, 'index')
indexDirectory = FSDirectory.open(index_dir)
searcher = new IndexSearcher(indexDirectory)

package_names = list_packages()
if (package_names.size() > 10) package_names = package_names[0..9]

try {

package_names.each { package_name ->
   def SRPM_SPEC_MIME_TYPE = "text/x-srpm-spec"

   use(Utils) {
       def info = package_doc(package_name)

       if (info) {
           def spec_expanded_file = new File(info."package.spec_expanded")

           def package_params = [(DOCUMENT_ENCODING_PARAMETER_NAME):"UTF-8"]
           def package_doc = (CompoundDocument) Factory.createResource("gate.compound.impl.CompoundDocumentImpl"
                   , package_params.toFeatureMap())

           def spec_expanded_doc = createDocument((DOCUMENT_URL_PARAMETER_NAME):spec_expanded_file.toURI().toURL()
                   , (DOCUMENT_ENCODING_PARAMETER_NAME):"UTF-8"
                   , (DOCUMENT_MIME_TYPE_PARAMETER_NAME): SRPM_SPEC_MIME_TYPE
                   , (DOCUMENT_MARKUP_AWARE_PARAMETER_NAME):false
                   , (DOCUMENT_PRESERVE_CONTENT_PARAMETER_NAME):true
                   , (DOCUMENT_REPOSITIONING_PARAMETER_NAME):true
           )

           package_doc.addDocument("package.spec_expanded", spec_expanded_doc)

           def spec_file = new File(info."package.spec")

           def spec_doc = createDocument((DOCUMENT_URL_PARAMETER_NAME):spec_file.toURI().toURL()
                   , (DOCUMENT_ENCODING_PARAMETER_NAME):"UTF-8"
                   , (DOCUMENT_MIME_TYPE_PARAMETER_NAME): SRPM_SPEC_MIME_TYPE
                   , (DOCUMENT_MARKUP_AWARE_PARAMETER_NAME):false
                   , (DOCUMENT_PRESERVE_CONTENT_PARAMETER_NAME):true
                   , (DOCUMENT_REPOSITIONING_PARAMETER_NAME):true
           )

           package_doc.addDocument("package.spec", spec_doc)

           package_doc.currentDocument = "package.spec_expanded"

           package_doc.name = package_name

           corpus.add(package_doc)
       }
   }
}

} finally {
    searcher.close()
}

corpus.each { println "${it.name} ${it.documentIDs}" }

List<String> list_packages()
{
    def query = new TermQuery(new Term('type', 'package'))

    def hits = searcher.search(query, 10000 /*, Sort.INDEXORDER*/)

    hits.scoreDocs.collect { hit ->
        def doc = searcher.doc(hit.doc)
        doc['package.name']
    }
}

def package_doc(String package_id)
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

def createDocument(Map<?, ?> params)
{
    Factory.createResource("gate.corpora.DocumentImpl", params.toFeatureMap())
}
