package scripts

import gate.Corpus
import gate.Document
import gate.Factory
import gate.Utils
import gate.compound.CompoundDocument

//Corpus corpus

LINUX_RPM = new File("/mnt/LINUX_RPM")

packages_dir = new File(LINUX_RPM, "packages")

index_dir = new File(LINUX_RPM, 'index')

package_dirs = packages_dir.listFiles()
if (package_dirs.size() > 10) package_dirs = package_dirs[0..9]

package_dirs.each { package_dir ->
   use(Utils) {
       def spec_file = new File(package_dir, "package.spec")

       if (spec_file.exists()) {
           def package_doc = (CompoundDocument) Factory.createResource("gate.compound.impl.CompoundDocumentImpl")

           def spec_params = [sourceUrl:spec_file.toURI().toURL(), preserveOriginalContent:true]
           def spec_doc = (Document) Factory.createResource("gate.corpora.DocumentImpl", spec_params.toFeatureMap())

           package_doc.addDocument("package.spec_expanded", spec_doc)

           corpus.add(package_doc)
       }
   }
}

