package scripts

import groovy.xml.StreamingMarkupBuilder
import org.apache.lucene.document.Document
import org.ifcx.readit.index.GATE_Processor
import org.ifcx.readit.index.Retriever

import javax.servlet.http.HttpServletRequest

queries_dir = new File('output/queries')
preprocessed_dir = new File('output/preprocessed')

retriever = new Retriever()
processor = new GATE_Processor()

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
    def file_name = urlparams.file_name ?: "README"
    new StreamingMarkupBuilder().bind {
        html {
            head { title('READMES') }
            body {
                p 'READMES'
                p {
                    def readmes = retriever.list_readmes(file_name)
                    readmes.each { Document doc ->
                        span(doc['package.name'] + ' ' + doc['file.name']); span(' '); a(href:href_file(doc['file.mime_type'], doc['file.path']), doc['file.build_path']); br()
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

get("/process/.*")
{
    HttpServletRequest req = request

    def (_, mime_type, mime_subtype, file_path) = (req.pathInfo =~ ~'/process/([^/]*)/([^/]*)(.*)')[0]

    response.setHeader('Content-Type','text/html')

    def xml = processor.taggedHTMLforFile(file_path)

    def html = """<html lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
	<title>Processed</title>
</head>
<style type="text/css">
BODY, body { margin: 2em } /* or any other first level tag */
P, p { display: block } /* or any other paragraph tag */
/* ANNIE tags but you can use whatever tags you want */
/* be careful that XML tags are case sensitive */
Sentence  { background-color: rgb(150, 230, 150) }
Token     { background-color: rgb(230, 150, 230) }
nounchunk     { background-color: rgb(230, 150, 230) }
Date         { background-color: rgb(230, 150, 150) }
FirstPerson  { background-color: rgb(150, 230, 150) }
Identifier   { background-color: rgb(150, 150, 230) }
JobTitle     { background-color: rgb(150, 230, 230) }
Location     { background-color: rgb(230, 150, 230) }
Money        { background-color: rgb(230, 230, 150) }
Organization { background-color: rgb(230, 200, 200) }
Percent      { background-color: rgb(200, 230, 200) }
Person       { background-color: rgb(200, 200, 230) }
Title        { background-color: rgb(200, 230, 230) }
Unknown      { background-color: rgb(230, 200, 230) }
Etc          { background-color: rgb(230, 230, 200) }
/* The next block is an example for having a small tag
   with the name of the annotation type after each annotation */
Date:after {
content: "Date";
font-size: 50%;
vertical-align: sub;
color: rgb(100, 100, 100);
}
</style>
<body>
<pre>
${xml}
</pre>
</body>
</html>
"""

//    println html

    html
}

get("/package/:package_id")
    {
        def package_id = urlparams.package_id
        def package_doc = retriever.package_doc(package_id)
        def file_docs = retriever.list_package_files(package_id, true)
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
                                }
                                td {
                                    a(href:href_process_file(doc.get('file.mime_type'), doc.get('file.path')), "process")
                                }
                                td {
                                    a(href:href_search(doc.get('file.path'), package_doc.get('package.spec_expanded'), "GNU ed is a line-oriented text editor." ), "search")
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

get("/search")
{
    def file_path = params.file_path
    def spec_file_path = params.spec_file_path
    def subpackage_id = params.subpackage_id
    def search_field = params.field
    def slop = (params.slop ?: "0") as int

//            def spec = retriever.parse_spec_file(spec_file_path)
//            if (spec) {
        new StreamingMarkupBuilder().bind {
            html {
                head { title('Searching in ' + subpackage_id) }
                body {
                    h1 'Package ' + subpackage_id
                    def ranges = retriever.searchInFile(file_path, search_field, slop)
                    ranges.each {
                        p(it)
                    }
                }
            }
        }.toString()
//            } else {
//                "RPM SPEC parse failed for $package_id"
//            }
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

String href_search(String file_path, String spec_file_path, String field)
{
    "/search?file_path=${file_path}&spec_file_path=${spec_file_path}&field=${field}"
}

String href_file(String mime_type, String file_path)
{
    '/file/' + mime_type + file_path
}

String href_process_file(String mime_type, String file_path)
{
    '/process/' + mime_type + file_path
}

