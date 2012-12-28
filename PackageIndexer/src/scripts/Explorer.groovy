package scripts

import com.bleedingwolf.ratpack.Ratpack
import com.bleedingwolf.ratpack.RatpackServlet
import groovy.xml.StreamingMarkupBuilder
import org.apache.lucene.document.Document
import org.ifcx.readit.index.ExtractionRules
import org.ifcx.readit.index.Model
import org.ifcx.readit.index.Retriever
import org.ifcx.readit.index.Slot

import javax.servlet.http.HttpServletRequest

queries_dir = new File('output/queries')
preprocessed_dir = new File('output/preprocessed')

//def app = Ratpack.app {
//
//    def retriever = new Retriever()
//
//    def package_names = retriever.list_packages()
//    def query_memo = [:].withDefault { query_info(it) }
//    def document_memo = [:].withDefault { document_info(it) }

//    def extraction_rules = new ExtractionRulesWithPatterns()

retriever = new Retriever()

package_names = retriever.list_packages()

new_slot = ''
new_pattern = ''
filter_values = true

    get("/") {
        new StreamingMarkupBuilder().bind {
            html {
                head { title('ReadIt Query Explorer') }
                body {
                    p "ReadIt Query Explorer"
                    p { a(href:'/packages', 'Packages')  }
                    p { a(href:'/readmes/INSTALL', 'READMES')  }
                }
            }
        }.toString()
    }

    get("/packages") {
        new StreamingMarkupBuilder().bind {
            html {
                head { title('Packages') }
                body {
                    p 'Packages'
//                    p "Queries"
                    p {
//                        package_names.each { id -> a(href:href_query(id), id); span(' ') ; span(query_memo[id].name ) ; br() }
                        package_names.each { package_name -> a(href:href_package(package_name), package_name) ; br() }
                    }
                }
            }
        }.toString()
    }

    get("/readmes/:file_name") {
        def file_name = urlparams.file_name ?: "INSTALL"
        new StreamingMarkupBuilder().bind {
            html {
                head { title('READMES') }
                body {
                    p 'READMES'
                    p {
                        def readmes = retriever.list_readmes(file_name)
                        readmes.each { Document doc ->
                            span(doc['package.name'] + ' ' + doc['file.name']); span(' '); a(href:href_file(doc['mime.type'], doc['file.path']), doc['file.build_path']); br()
                        }
                    }
                }
            }
        }.toString()
    }

    def file_path_pattern = ~'/file/([^/]*)/([^/]*)(.*)'

    get("/file/.*")
    {
        HttpServletRequest req = request

        def (_, mime_type, mime_subtype, file_path) = (req.pathInfo =~ file_path_pattern)[0]
//        file_path = file_path.replaceAll("^//", "/")
//        println "$mime_type/$mime_subtype $file_path"

        response.setHeader('Content-Type', mime_type + '/' + mime_subtype)

        new File(file_path).text
    }

    get("/package/:package_id")
            {
                def package_id = urlparams.package_id
                def package_doc = retriever.package_doc(package_id)
                def file_docs = retriever.list_package_files(package_id)
                file_docs = file_docs.sort { it.get('file.shebang')+ '~' + it.get('file.mime_type') + '~' + it.get('file.name') }
                new StreamingMarkupBuilder().bind {
                    html {
                        head { title('Package ' + package_id) }
                        body {
                            p 'Package ' + package_id
                            table {
                                tr {
                                    td('Name')
                                    td(package_id)
                                }
                                tr {
                                    td('Spec')
                                    td {
                                        def link = href_package_field(package_id, 'package.spec')
                                        a(href:link, link)
                                        span(' - ')
                                        a(href:href_parse_spec(package_id, package_doc.get('package.spec')), 'parsed')
                                    }
                                }
                                tr {
                                    td('Expanded Spec')
                                    td {
                                        def link = href_package_field(package_id, 'package.spec_expanded')
                                        a(href:link, link)
                                        span(' - ')
                                        a(href:href_parse_spec(package_id, package_doc.get('package.spec_expanded')), 'parsed')
                                    }
                                }
                                tr {
                                    td('Files')
                                    td(file_docs.size())
                                }
                            }
                            p()
                            table {
                                file_docs.each { doc ->
                                    tr {
                                        def link = href_file(doc.get('file.mime_type'), doc.get('file.path'))
                                        td(doc.get('file.mime_type'))
                                        td(doc.get('file.shebang'))
                                        td(doc.get('file.name'))
                                        td(doc.get('file.size'))
                                        td {
                                            a(href:link, doc.get('file.build_path'))
//                                            a(href:link, doc.get('file.path'))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.toString()
            }

    get("/package/:package_id/:field")
            {
                def package_id = urlparams.package_id
                def field = urlparams.field
                def package_doc = retriever.package_doc(package_id)
                def file_path = package_doc?.get(field)
                println file_path
                if (file_path) {
                    response.setHeader('Content-Type', 'text/x-specfile')
                    new File(file_path).text
                } else {
                    "Missing doc or field for $package_id $field"
                }
            }

    get("/spec/:package_id")
            {
                def package_id = urlparams.package_id
                def spec = retriever.spec_for_package(package_id)
                if (spec) {
                    new StreamingMarkupBuilder().bind {
                        html {
                            head { title('Package ' + package_id) }
                            body {
                                h1 'Package ' + package_id
                                spec.keySet().sort().each { subpackage_name ->
                                    h2(package_id + '-' + subpackage_name)
                                    def section_values = spec[subpackage_name]
                                    table(border:'1') {
                                        section_values.keySet().sort { it.token }.each { section ->
                                            tr {
                                                td(section.name())
                                                td {
                                                    pre(section_values[section])
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.toString()
                } else {
                    "RPM SPEC parse failed for $package_id"
                }
            }

get("/showspec")
        {
            def package_id = params.package_id
            def spec_file_path = params.spec_file_path
            def spec = retriever.parse_spec_file(spec_file_path)
            if (spec) {
                new StreamingMarkupBuilder().bind {
                    html {
                        head { title('Package ' + package_id) }
                        body {
                            h1 'Package ' + package_id
                            spec.keySet().sort().each { subpackage_name ->
                                h2(package_id + '-' + subpackage_name)
                                def section_values = spec[subpackage_name]
                                table(border:'1') {
                                    section_values.keySet().sort { it.token }.each { section ->
                                        tr {
                                            td(section.name())
                                            td {
                                                pre(section_values[section])
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.toString()
            } else {
                "RPM SPEC parse failed for $package_id"
            }
        }

get("/query/:query_id")
    {
        def query = query_memo[urlparams.query_id] // query_info(urlparams.query_id)

        new StreamingMarkupBuilder().bind {
            html {
                head { title("${query.id} : ${query.name}") }
                body {
                    p {
                        a(href:'/', 'Main')
                        span ' '
                        def query_id_index = query_ids.findIndexOf { it == query.id }
                        if (query_id_index > 0) {
                            def prev_id = query_ids[query_id_index - 1]
                            a(href:href_query(prev_id), prev_id)
                            span ' '
                        }
                        if (query_id_index + 1 < query_ids.size()) {
                            def next_id = query_ids[query_id_index + 1]
                            a(href:href_query(next_id), next_id)
                            span ' '
                        }
                    }
                    p {
                        span "Query ${query.id}"
                        span ' for '
                        span query.name
                    }

//                    pre query.slots.join('\n')
//                    pre query.doc_ids.join('\n')

//                    def retriever = new Retriever(query.folder, preprocessed_dir)
                    def rules = retriever.extraction_rules.rules
                    println rules
                    def answer_map = retriever.do_all_documents()
                    table(border:'1') {
                        (answer_map.keySet() as List).sort { it.label }.each { slot ->
                            def answers = answer_map[slot]
                            (answers as List).sort { a, b -> a.value <=> b.value ?: a.doc_id <=> b.doc_id ?: a.sent_num <=> b.sent_num }.each { answer ->
                                tr {
                                    td answer.slot.label
                                    td { a(href:href_document(query, answer.doc_id), answer.doc_id) }
                                    td { a(href:href_sentence(query, answer.doc_id, answer.sent_num), answer.sent_num) }
                                    td answer.value
                                }
                            }

//                            if (answers) {
//                                tr {
//                                    td() {
//                                        span slot.label
//                                    }
//                                    answers.each { answer ->
//                                        td answer.doc_id
//                                        td answer.sent_num
//                                        td answer.value
//                                    }
//                                }
//                            } else {
//                                tr {
//                                    td slot.label
//                                    td (colspan:'3')
//                                }
//                            }
                        }
                    }

                    query.doc_ids.each { doc_id ->
                        p { a(href:href_document(query, doc_id), doc_id) }
                    }
                }
            }
        }.toString()
    }

    get("/query/:query_id/document/:doc_id")
    {
        def query = query_memo[urlparams.query_id]
        def document = document_memo[urlparams.doc_id]

        new StreamingMarkupBuilder().bind {
            html {
                head { title("${query.id} : ${query.name} : ${document.id}") }
                body {
                    p {
                        a(href:'/', 'Main')
                        span ' '
                        a(href:href_query(query.id), query.id)
                        span ' '
                        def doc_id_index = query.doc_ids.findIndexOf { it == document.id }
                        if (doc_id_index > 0) {
                            def prev_id =  query.doc_ids[doc_id_index - 1]
                            a(href:href_document(query, prev_id), prev_id)
                            span ' '
                        }
                        if (doc_id_index + 1 <  query.doc_ids.size()) {
                            def next_id =  query.doc_ids[doc_id_index + 1]
                            a(href:href_document(query, next_id), next_id)
                            span ' '
                        }
                    }
                    p {
                        span "Query ${query.id}"
                        span ' for '
                        span query.name
                    }

                    def annotation = document.annotation

                }
            }
        }.toString()
    }

    get("/query/:query_id/document/:doc_id/sentence/:sent_num")
    {
        def query = query_memo[urlparams.query_id]
        def document = document_memo[urlparams.doc_id]
        def sent_num = urlparams.sent_num as Integer

        sentence_response(query, document, sent_num, new_slot, new_pattern, filter_values)
    }

    post("/newpattern")
    {
        def query = query_memo[params.query_id]
        def document = document_memo[params.doc_id]
        def sent_num = params.sent_num as Integer

        new_slot = params.new_slot
        new_pattern = params.new_pattern

        println params.filter_values

        filter_values = params.filter_values == "true"

        Slot.disableSlotFilters = !filter_values

        sentence_response(query, document, sent_num, new_slot, new_pattern, filter_values)
    }

def sentence_response(query, document, sent_num, String new_slot, String new_pattern, boolean filter_values)
{
    new StreamingMarkupBuilder().bind {
        html {
            head { title("${query.id} : ${query.name} : ${document.id} : ${sent_num}") }
            body {
                p {
                    a(href: '/', 'Main')
                    span ' '
                    a(href: href_query(query.id), query.id)
                    span ' '
                    def doc_id_index = query.doc_ids.findIndexOf { it == document.id }
                    if (doc_id_index > 0) {
                        def prev_id = query.doc_ids[doc_id_index - 1]
                        a(href: href_document(query, prev_id), prev_id)
                        span ' '
                    }
                }
                p {
                    span "Query ${query.id}"
                    span ' for '
                    span query.name
                }
                p {
                    a(href: href_document(query, document.id), document.id)
                    span ' sentence '
                    if (sent_num > 1) {
                        def prev_sent_num = (sent_num - 1) as String
                        a(href: href_sentence(query, document.id, prev_sent_num), prev_sent_num)
                        span ' '
                    }
                    span sent_num
                    span ' '
                    if (sent_num /* < # of sentences in document */) {
                        def next_sent_num = (sent_num + 1) as String
                        a(href: href_sentence(query, document.id, next_sent_num), next_sent_num)
                        span ' '
                    }
                }

                p {
                    form(action:'/newpattern', method: 'POST') {
                        input(name:'query_id', type:'hidden', value:query.id)
                        input(name:'doc_id', type:'hidden', value:document.id)
                        input(name:'sent_num', type:'hidden', value:sent_num)
                        if (filter_values) {
                            input(name:'filter_values', type:'checkbox', value:'true', checked:'checked') { span 'filter' }
                        } else {
                            input(name:'filter_values', type:'checkbox', value:'true') { span 'filter' }
                        }
                        select(name:'new_slot') {
                            Model.slots.each { label, slot ->
                                if (new_slot == label) {
                                    option(value:label, selected:'selected', label)
                                } else {
                                    option(value:label, label)
                                }
                            }
                        }
                        span ' pattern: '
                        input(name: 'new_pattern', size:'80', value:new_pattern)
                    }

//                    if (pattern && sentence) {
//                        br()
//                        def answers_for_pattern = retrive_one_pattern(pattern, sentence)
//                    }
                }

                def retriever = new Retriever(query.folder, preprocessed_dir)

                if (new_slot && new_pattern) retriever.extraction_rules.rule(new_slot, new_pattern)

                retriever.do_document(document.id)
                def answer_map = retriever.all_answers

                table(border: '1') {
                    (answer_map.keySet() as List).sort { it.label }.each { slot ->
                        def answers = answer_map[slot]
                        (answers as List).sort { a, b -> a.value <=> b.value }.each { answer ->
                            if (answer.doc_id == document.id && answer.sent_num == sent_num) {
                                tr {
                                    td answer.slot.label
                                    td answer.value
                                    td {
                                        pre answer.pattern
                                    }
                                }
                            }
                        }
                    }
                }

                def annotation = document.annotation

//                List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class)
//
//                def sentence = sentences[sent_num - 1]
//
//                if (sentence) {
//
//                    p sentence
//
//                    SemanticGraph g = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class)
//                    g = new SemanticGraph(g)
//                    Set<IndexedWord> vertexSet = g.vertexSet()
//
//                    // Don't leave our marks around in case the sentence is processed again.
//                    // Not entirely clear to me what sharing goes on, but this does appear to be necessary for that.
//                    //                    vertexSet.each { it.set(CoreAnnotations.FeaturesAnnotation, '') }
//
//                    Map<Integer, CorefChain> coref_chains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class)
//                    //                    println query.name
//                    coref_chains.each { coref_num, CorefChain coref_chain ->
//                        //                        println coref_chain
//                        if (Retriever.coref_chain_mentions_name(coref_chain, query.name)) {
//                            //                            println "CorefChain has mentions"
//                            coref_chain.corefMentions.each { mention ->
//                                if (mention.sentNum == sent_num) {
//                                    //                                    println "Sentence has mentions"
//                                    // Mark the nodes for all tokens (that have one) except the head token as being {features:mentionToken}.
//                                    ((mention.startIndex..<mention.endIndex) - [mention.headIndex]).each { x ->
//                                        // If we're using collapsed dependencies (as we are), not all tokens will have a node in the graph.
//                                        (vertexSet.find { it.index() == x })?.set(CoreAnnotations.FeaturesAnnotation, 'mentionToken')
//                                    }
//
//                                    // Mark the node for the head token as {features:mention}.
//                                    def headIndex = vertexSet.find { it.index() == mention.headIndex }
//                                    headIndex?.set(CoreAnnotations.FeaturesAnnotation, 'mention')
//                                }
//                            }
//                        }
//                    }
//
//                    g = new SemanticGraph(g)
                    //                    println headIndex?.get(CoreAnnotations.FeaturesAnnotation)
                    //                    def xxx = headIndex ? headIndex.index() : -1
                    //                            { ->
                    //                                def z = g.vertexSet().find { it.index() == xxx }  ;
                    //                                println (z == null ? 'zzz' : 'Z '+ z.toString())
                    //                                def han = z?.get(CoreAnnotations.FeaturesAnnotation)
                    //                                println han
                    //                                println han?.class
                    //                            }()

                    pre ExplorerUtils.toString(g)
//                } else {
//                    p '<EOD/>'
//                }
            }
        }
    }.toString()
}

def retrive_one_pattern(slot, pattern, doc_id, sent_num, sentence, g)
{
    def retriever = new Retriever()
    def extraction_rules = new ExtractionRules()
    extraction_rules.rule(slot, pattern)
    retriever.extraction_rules = extraction_rules
    retriever.do_sentence(doc_id, sent_num, sentence, g)
}

String href_package(String package_id)
{
    '/package/'+package_id
}

String href_package_field(String package_id, String field)
{
    '/package/'+package_id+'/'+field
}

String href_package_spec(String package_id)
{
    '/spec/'+package_id
}

String href_parse_spec(String package_id, String spec_file_path)
{
    "/showspec?package_id=${package_id}&spec_file_path=${spec_file_path}"
}

String href_file(String mime_type, String file_path)
{
    '/file/' + mime_type + file_path
}

String href_query(String query_id)
{
    '/query/'+query_id
}

String href_document(Map query, document_id)
{
    '/query/'+query.id+'/document/'+document_id
}

String href_sentence(Map query, document_id, sent_num)
{
    '/query/'+query.id+'/document/'+document_id+'/sentence/'+ sent_num
}

def query_info(String query_id)
{
    def query_folder = new File(queries_dir, query_id)
    def documents_file = new File(query_folder, 'documents.txt')

    def doc_ids = documents_file.readLines().collect { new File(it).name - ~/\.sgm$/ }.sort()

    def query_name = new File(query_folder, 'name.txt').text.trim()
    def slots = new File(query_folder, 'slots.txt').readLines().sort()

    [id:query_id, name:query_name, slots:slots, doc_ids:doc_ids, folder:query_folder]
}

def document_info(String doc_id)
{
//    Annotation annotation = null
//
//    def preprocessed_file = new File(preprocessed_dir, doc_id + ".sgm.ser.gz")
//
//    if (!(preprocessed_file.exists() ))  {
//        println "file ${preprocessed_file} doesn't exist"
//    } else {
//        try {
//            annotation = IOUtils.readObjectFromFile(preprocessed_file)
//        } catch (Exception e) {
//            println e
//        }
//    }
//
//    [id: doc_id, annotation:annotation]
}
