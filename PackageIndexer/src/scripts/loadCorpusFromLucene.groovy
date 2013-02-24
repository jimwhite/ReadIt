package scripts

import gate.Corpus
import gate.Document
import gate.Factory
import gate.Utils
import gate.compound.CompoundDocument

import static gate.Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME
import static gate.Document.DOCUMENT_ENCODING_PARAMETER_NAME
import static gate.Document.DOCUMENT_MARKUP_AWARE_PARAMETER_NAME
import static gate.Document.DOCUMENT_PRESERVE_CONTENT_PARAMETER_NAME
import static gate.Document.DOCUMENT_REPOSITIONING_PARAMETER_NAME
import static gate.SimpleDocument.DOCUMENT_URL_PARAMETER_NAME

//Corpus corpus

LINUX_RPM = new File("/mnt/LINUX_RPM")

packages_dir = new File(LINUX_RPM, "packages")

index_dir = new File(LINUX_RPM, 'index')

package_dirs = packages_dir.listFiles()
if (package_dirs.size() > 10) package_dirs = package_dirs[0..9]

package_dirs.each { package_dir ->
   def SRPM_SPEC_MIME_TYPE = "text/x-srpm-spec"
   use(Utils) {
       def spec_expanded_file = new File(package_dir, "package.spec")

       if (spec_expanded_file.exists()) {
           def package_params = [(DOCUMENT_ENCODING_PARAMETER_NAME):"UTF-8"]
           def package_doc = (CompoundDocument) Factory.createResource("gate.compound.impl.CompoundDocumentImpl", package_params.toFeatureMap())

           def spec_expanded_doc = createDocument((DOCUMENT_URL_PARAMETER_NAME):spec_expanded_file.toURI().toURL()
                   , (DOCUMENT_ENCODING_PARAMETER_NAME):"UTF-8"
                   , (DOCUMENT_MIME_TYPE_PARAMETER_NAME): SRPM_SPEC_MIME_TYPE
                   , (DOCUMENT_MARKUP_AWARE_PARAMETER_NAME):true
                   , (DOCUMENT_PRESERVE_CONTENT_PARAMETER_NAME):true
                   , (DOCUMENT_REPOSITIONING_PARAMETER_NAME):true
           )

           package_doc.addDocument("package.spec_expanded", spec_expanded_doc)

//           def spec_file = new File(package_dir, "package.spec_expanded")
//
//           def spec_doc = createDocument((DOCUMENT_URL_PARAMETER_NAME):spec_file.toURI().toURL()
//                   , (DOCUMENT_ENCODING_PARAMETER_NAME):"UTF-8"
//                   , (DOCUMENT_MIME_TYPE_PARAMETER_NAME): SRPM_SPEC_MIME_TYPE
//                   , (DOCUMENT_MARKUP_AWARE_PARAMETER_NAME):true
//                   , (DOCUMENT_PRESERVE_CONTENT_PARAMETER_NAME):true
//                   , (DOCUMENT_REPOSITIONING_PARAMETER_NAME):true
//           )
//
//           package_doc.addDocument("package.spec", spec_doc)

           corpus.add(package_doc)
       }
   }
}

Document createDocument(Map<?, ?> params)
{
    (Document) Factory.createResource("gate.corpora.DocumentImpl", params.toFeatureMap())
}
