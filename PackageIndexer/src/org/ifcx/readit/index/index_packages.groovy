#!/usr/bin/env groovy

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.util.Version
import org.apache.lucene.document.Document
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.document.Field
import org.cyberneko.html.filters.ElementRemover
import org.apache.xerces.xni.parser.XMLParserConfiguration
import org.cyberneko.html.HTMLConfiguration
import org.apache.xerces.xni.parser.XMLInputSource
import org.apache.xerces.xni.parser.XMLDocumentFilter
import org.apache.xerces.xni.XMLLocator
import org.apache.xerces.xni.NamespaceContext
import org.apache.xerces.xni.Augmentations
import org.apache.xerces.xni.XNIException
import org.cyberneko.html.xercesbridge.XercesBridge
import org.apache.xerces.xni.XMLString
import org.apache.xerces.xni.QName
import org.apache.xerces.xni.XMLAttributes
import org.apache.xerces.xni.XMLResourceIdentifier
import org.apache.lucene.document.NumericField

args = args as List

LINUX_RPM = new File("/mnt/LINUX_RPM")

PACKAGE_INFO = new File(LINUX_RPM, "packages")

index_dir = new File(LINUX_RPM, 'index')

release_id = 'fc17'
packages_dir = PACKAGE_INFO

if (args && args[0] == '--index') {
    args.remove(0)
    index_dir = new File(args.remove(0))
}

if (args && args[0] == '--packages') {
    args.remove(0)
    packages_dir = new File(args.remove(0))
}

if (args && args[0] == '--release_id') {
    args.remove(0)
    release_id = args.remove(0)
}

println "index_dir      = ${index_dir}"
println "packages_dir   = ${packages_dir}"
println "release_id     = ${release_id}"

FILE_SIZE_LIMIT = 1000000

//def fvpat = ~/^(\w+)='?([^']*)'?/
//def foom = ("FOO=123" =~ fvpat)
//println foom.matches()
//println (foom.groupCount())
//println (foom.group(1))
//println (foom.group(2))
//assert ["FOO=123", "FOO", "123"] == ("FOO=123" =~ fvpat)[0]
//assert ["FOO='123'", "FOO", "123"] == ("FOO='123'" =~ fvpat)[0] as List

indexDirectory = FSDirectory.open(index_dir)

indexWriter = open_index_writer()

//writer.useCompoundFile = false
package_count = 0
file_count = 0

packages_dir.eachFile { package_dir ->
    if (package_dir.isDirectory()) {
//        println package_dir
        def info = read_package_info_fields(package_dir)

        println info.name

        try {
            index_package(info, package_dir)
        } catch (Error error1) {
            error1.printStackTrace(System.out)
            println("Closing index...")
            indexWriter.close()
            println("Reopening index...")
            indexWriter = open_index_writer()
            println("Retrying on " + info.name)
            try {
                index_package(info, package_dir)
                println("Retry success!")
            } catch (Error error2) {
                println("Retry failed!")
                error2.printStackTrace(System.out)
                println("Closing index...")
                indexWriter.close()
                println("Reopening index...")
                indexWriter = open_index_writer()
                println("Skipping " + info.name)
            }
        }
    }
}

indexWriter.close()

println "Added ${package_count} packages with ${file_count} files to index."

IndexWriter open_index_writer()
{
    new IndexWriter(indexDirectory, new IndexWriterConfig(Version.LUCENE_CURRENT, new StandardAnalyzer(Version.LUCENE_CURRENT)))
}

def void index_package(Map info, package_dir)
{
    // name
    // spec
    // release
    // build files dir path

    def package_doc = new Document()

    def typeField = new Field("type", "package", Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
    typeField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
    package_doc.add(typeField)

    def releaseField = new Field("release", release_id, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
    releaseField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
    package_doc.add(releaseField)

    def packageNameField = new Field("package.name", info.name, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
    packageNameField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
    package_doc.add(packageNameField)

    def specField = new Field("package.spec", info.spec, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
    specField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
    package_doc.add(specField)

    String build_dir_name = info.build_dir

    def build_files_dir = new File(package_dir, build_dir_name)

    def packagePathField = new Field("package.build_files_path", build_files_dir.absolutePath, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
    packagePathField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
    package_doc.add(packagePathField)

    add_package_list_fields(package_doc, package_dir, "buildrequires.txt", "buildrequires")
    add_package_list_fields(package_doc, package_dir, "requires.txt", "requires")
    add_package_list_fields(package_doc, package_dir, "provides.txt", "provides")

    // requires*        trim ' = ...' and unique
    // buildrequires*   "  "
    // provides*        "  "
    // each file:
    //    mime (as guessed by file command)
    //    extension

    def fileTypeField = new Field("type", "file", Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
    fileTypeField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)

    def build_files_list = new File(package_dir, "file_types.txt").readLines().collect { it.trim() }.grep()

    build_files_list.each { String file_path_and_type ->
        // Mime type follows the null char.
        def file_mime_type = file_path_and_type.substring(file_path_and_type.indexOf(0) + (" : ".length()))

        // We don't really need the guessed charset.
        if (file_mime_type.indexOf(';') > 0) {
            file_mime_type = file_mime_type.substring(0, file_mime_type.indexOf(';'))
        }

        def build_file_path = file_path_and_type.substring(0, file_path_and_type.indexOf(0))

        try {
            def build_file = new File(package_dir, build_file_path)

            build_file_path = build_file_path.substring(build_dir_name.length() + 1)

            if (file_mime_type.startsWith("text") && build_file.exists()) {
                def file_name = build_file.name
                def extension = file_name.contains(' ') ? file_name.toLowerCase().substring(file_name.lastIndexOf(' ') + 1) : ".none."

                if ((file_mime_type == "text/html") && (extension in ["c", "h", "cpp", "hpp", "cxx", "hxx", "py", "java"])) {
                    file_mime_type = "text/plain"
                }

                def file_size = build_file.size()

                def build_file_doc = new Document()

                package_doc.add(fileTypeField)
                package_doc.add(releaseField)
                package_doc.add(packageNameField)

                def filePathField = new Field("file.path", build_file_path, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
                filePathField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
                package_doc.add(filePathField)

                def fileNameField = new Field("file.name", file_name, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
                fileNameField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
                package_doc.add(fileNameField)

                def fileExtField = new Field("file.extension", extension, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
                fileExtField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
                package_doc.add(fileExtField)

                def fileMimeTypeField = new Field("file.mime_type", file_mime_type, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
                fileMimeTypeField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
                package_doc.add(fileMimeTypeField)

                def fileSizeField = new NumericField("file.size", Field.Store.YES, false)
                fileSizeField.setLongValue(file_size)
                package_doc.add(fileSizeField)

                if (file_size < FILE_SIZE_LIMIT) {
                    def file_contents = build_file.text

                    if (file_mime_type in ["text/html", "text/xhtml"]) {
                        file_contents = html_to_text(file_contents)
                    }

                    def textField = new Field("contents", file_contents, Field.Store.NO, Field.Index.ANALYZED)
                    textField.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
                    build_file_doc.add(textField)
                }

                indexWriter.addDocument(build_file_doc)

                ++file_count
            }
        } catch (Exception e) {
            e.printStackTrace(System.out)
        }
    }

    indexWriter.addDocument(package_doc)
    
    indexWriter.commit()

    ++package_count
}

def void add_package_list_fields(Document package_doc, File package_dir, String package_list_file_name, String field_name)
{
    def packages = new File(package_dir, package_list_file_name).readLines().collect { package_name_from_selector(it) }.grep()

    packages.each { String package_name ->
        def releaseNameField = new Field(field_name, package_name, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS)
        releaseNameField.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY)
        package_doc.add(releaseNameField)
    }
}

def void package_name_from_selector(String it)
{
    (it.contains(" ") ? it.substring(0, it.indexOf(' ')) : it).trim()
}

def read_package_info_fields(File package_info_dir)
{
    def result = new File(package_info_dir, "status.txt").readLines().collect { it.trim() }.grep().collectEntries { line ->
        def (_, fname, fvalue) = (line =~ /^(\w+)='?([^']*)'?/)[0]
        [fname, fvalue]
    }
    result
}

def html_to_text(String html)
{
    String[] safeTags = ["b","i","a","p","br","pre", "center"
            , "address", "blockquote", "code", "em", "strong", "big", "small", "cite"
            , "ul", "ol", "li", "dl", "lh", "dt", "dd"
            , "table","tr","td","tbody","th"
            ,"h1","h2","h3","h4","h5","h6"]
    String[] safeAttributes = ["href"];

    // we need to filter description to remove all script tags
    ElementRemover remover = new ElementRemover();
    for (String tag : safeTags) {
        remover.acceptElement(tag, safeAttributes);
    }

    remover.removeElement("script");

    // writer
    StringWriter filteredDescription = new StringWriter();
    XMLDocumentFilter writer = new TextWriter(filteredDescription);

    // setup filter chain
    XMLDocumentFilter[] filters = [remover, writer]

    // create HTML parser
    XMLParserConfiguration parser = new HTMLConfiguration();
    parser.setProperty("http://cyberneko.org/html/properties/filters", filters);
    XMLInputSource source = new XMLInputSource(null, null, null, new StringReader(html), null);

    parser.parse(source);

    filteredDescription.toString()
}

class TextWriter extends org.cyberneko.html.filters.DefaultFilter
{
    PrintWriter printer

    TextWriter(StringWriter sw)
    {
        printer = new PrintWriter(sw)
    }

    /** Start document. */
    public void startDocument(XMLLocator locator, String encoding,
                              NamespaceContext nscontext, Augmentations augs)
    throws XNIException
    {
    } // startDocument(XMLLocator,String,Augmentations)

    // old methods

    /** XML declaration. */
    public void xmlDecl(String version, String encoding, String standalone, Augmentations augs)
    throws XNIException
    {
    } // xmlDecl(String,String,String,Augmentations)

    /** Doctype declaration. */
    public void doctypeDecl(String root, String publicId, String systemId, Augmentations augs)
    throws XNIException
    {
    } // doctypeDecl(String,String,String,Augmentations)

    /** Comment. */
    public void comment(XMLString text, Augmentations augs)
    throws XNIException
    {
    } // comment(XMLString,Augmentations)

    /** Processing instruction. */
    public void processingInstruction(String target, XMLString data, Augmentations augs)
    throws XNIException
    {
    } // processingInstruction(String,XMLString,Augmentations)

    /** Start element. */
    public void startElement(QName element, XMLAttributes attributes, Augmentations augs)
    throws XNIException
    {
    } // startElement(QName,XMLAttributes,Augmentations)

    /** Empty element. */
    public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs)
    throws XNIException
    {
        if (element.localpart in ["BR", "P"]) printer.println()
    } // emptyElement(QName,XMLAttributes,Augmentations)

    /** Characters. */
    public void characters(XMLString text, Augmentations augs)
    throws XNIException
    {
        printer.print(text.toString())
    } // characters(XMLString,Augmentations)

    /** Ignorable whitespace. */
    public void ignorableWhitespace(XMLString text, Augmentations augs)
    throws XNIException
    {
        printer.println(text.toString())
    } // ignorableWhitespace(XMLString,Augmentations)

    /** Start general entity. */
    public void startGeneralEntity(String name, XMLResourceIdentifier id, String encoding, Augmentations augs)
    throws XNIException
    {
    } // startGeneralEntity(String,XMLResourceIdentifier,String,Augmentations)

    /** Text declaration. */
    public void textDecl(String version, String encoding, Augmentations augs)
    throws XNIException
    {
    } // textDecl(String,String,Augmentations)

    /** End general entity. */
    public void endGeneralEntity(String name, Augmentations augs)
    throws XNIException
    {
    } // endGeneralEntity(String,Augmentations)

    /** Start CDATA section. */
    public void startCDATA(Augmentations augs) throws XNIException
    {
    } // startCDATA(Augmentations)

    /** End CDATA section. */
    public void endCDATA(Augmentations augs) throws XNIException
    {
    } // endCDATA(Augmentations)

    /** End element. */
    public void endElement(QName element, Augmentations augs)
    throws XNIException
    {
        if (element.localpart in ["BR", "P", "TR"]) printer.println()
        if (element.localpart == "TD") printer.println()
    } // endElement(QName,Augmentations)

    /** End document. */
    public void endDocument(Augmentations augs) throws XNIException
    {
        printer.flush()
    } // endDocument(Augmentations)

    // removed since Xerces-J 2.3.0

    /** Start document. */
    public void startDocument(XMLLocator locator, String encoding, Augmentations augs)
    throws XNIException
    {
        startDocument(locator, encoding, null, augs);
    } // startDocument(XMLLocator,String,Augmentations)

    /** Start prefix mapping. */
    public void startPrefixMapping(String prefix, String uri, Augmentations augs)
    throws XNIException
    {
    } // startPrefixMapping(String,String,Augmentations)

    /** End prefix mapping. */
    public void endPrefixMapping(String prefix, Augmentations augs)
    throws XNIException
    {
    } // endPrefixMapping(String,Augmentations)

}
