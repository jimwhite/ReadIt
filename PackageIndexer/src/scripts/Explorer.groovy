package scripts

import groovy.xml.StreamingMarkupBuilder
import jaligner.Alignment
import jaligner.Sequence
import jaligner.SmithWatermanGotoh
import jaligner.formats.Pair
import jaligner.util.Commons
import jaligner.util.JaroWinklerSimilarity
import jaligner.util.LevenshteinDistance
import org.apache.lucene.analysis.Token
import org.apache.lucene.analysis.WhitespaceAnalyzer
import org.apache.lucene.document.Document
import org.ifcx.readit.index.GATE_Processor
import org.ifcx.readit.index.RPM_SPEC
import org.ifcx.readit.index.Retriever

import javax.servlet.http.HttpServletRequest

queries_dir = new File('output/queries')
preprocessed_dir = new File('output/preprocessed')

retriever = new Retriever()
processor = new GATE_Processor()
analyzer = new WhitespaceAnalyzer()

package_ids = retriever.list_packages().sort()

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
    def start_index = (params.start ?: "0") as Integer
    def limit_index = [start_index + (params.limit ? params.limit as Integer : 25), package_ids.size()].min()

    new StreamingMarkupBuilder().bind {
        html {
            head { title('Packages') }
            body {
                h1 'Packages'
                table(border:1) {
                    tr { td("PackageID") ; td("SubID"); td("Group")  ; td("Version") ; td("URL") ; td("License") ; td("Summary")}
                    package_ids[start_index..<limit_index].each { package_id ->
                        def spec = retriever.spec_for_package(package_id, true)
                        spec.each { sub_id, Map sections ->
                            String subpackage_id = sub_id ? "$package_id-$sub_id" : package_id
                            def package_section = sections[RPM_SPEC.Section.PACKAGE]
                            if (package_section) {
                                List fields = RPM_SPEC.parse_package_section(package_section)
                                tr {
                                    td { a(href: href_package(package_id), package_id) }
                                    td (sub_id)
                                    td (getField(fields, "group"))
                                    td (getField(fields, "version"))
                                    td (getField(fields, "url"))
                                    td (getField(fields, "license"))
                                    td (getField(fields, "summary"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }.toString()
}

private String getField(List fields, String fn) {
    (fields.find { it.key == fn }?.value) ?: ''
}

get("/packages-tsv") {
    def sw = new StringWriter()
    sw.withPrintWriter { printer ->
        printer.println (["PackageID", "PackageURL", "SubID", "Group", "Version", "URL", "License", "Summary"].join('\t'))
        package_ids.each { package_id ->
            def spec = retriever.spec_for_package(package_id, true)
            spec.each { sub_id, Map sections ->
                String subpackage_id = sub_id ? "$package_id-$sub_id" : package_id
                def package_section = sections[RPM_SPEC.Section.PACKAGE]
                if (package_section) {
                    List fields = RPM_SPEC.parse_package_section(package_section)
                    printer.println ([package_id, href_package(package_id), sub_id,
                            getField(fields, "group"), getField(fields, "version"),
                            getField(fields, "url"), getField(fields, "license"),
                            getField(fields, "summary")].join('\t'))
                }
            }
        }
    }
    sw.toString()
}

Map get_package_stats() {
    Map package_stats = [:] as TreeMap
    package_ids.each { package_id ->
        def expanded_spec = ((Retriever)retriever).spec_for_package(package_id, true)
        def spec = retriever.spec_for_package(package_id)

        expanded_spec.each { sub_id, Map sections ->
            String subpackage_id = sub_id ? "$package_id-$sub_id" : package_id
            def package_section = sections[RPM_SPEC.Section.PACKAGE]
            if (package_section) {
                def hasBUILD = sections.containsKey(RPM_SPEC.Section.BUILD)
                def build_section_line_count = 0

                if (hasBUILD) {
                    def spec_sections = spec[sub_id]
                    String build_section = spec_sections[RPM_SPEC.Section.BUILD]
                    build_section?.eachLine {
                        it = it.trim()
                        if (it && !it.startsWith('#')) build_section_line_count += 1
                    }
                }

                package_stats[subpackage_id] = [
                        package_id:package_id, subpackage_id:subpackage_id
                        , hasBUILD: hasBUILD, build_section_line_count:build_section_line_count
                        , requires:[] as Set, buildRequires:[] as Set
                        , requires_dependents:[] as Set, buildRequires_dependents:[] as Set
                ]
            } else {
                println "Warning: Missing PACKAGE section for $subpackage_id in $package_id"
            }
        }
    }

    package_ids.each { package_id ->
        def spec = retriever.spec_for_package(package_id, true)

        spec.each { sub_id, Map sections ->
            String subpackage_id = sub_id ? "$package_id-$sub_id" : package_id
            def stats = package_stats[subpackage_id]
            def package_section = sections[RPM_SPEC.Section.PACKAGE]
            if (stats && package_section) {
                List fields = RPM_SPEC.parse_package_section(package_section)
                fields.each { field ->
                    switch (field.key) {
                        case 'requires' :
                            def requires_dependencies = parse_dependency_list(field.value)
                            requires_dependencies = requires_dependencies.grep { package_stats[it]?.hasBUILD }
                            stats.requires += requires_dependencies
                            if (stats.hasBUILD)
                                requires_dependencies.each { package_stats[it].requires_dependents << subpackage_id }
                            break
                        case 'buildrequires' :
                            def buildRequires_dependencies = parse_dependency_list(field.value)
                            buildRequires_dependencies = buildRequires_dependencies.grep { package_stats[it]?.hasBUILD }
                            stats.buildRequires += buildRequires_dependencies
                            if (stats.hasBUILD)
                                buildRequires_dependencies.each { package_stats[it].buildRequires_dependents << subpackage_id }
                            break
                    }
                }
            }
        }
    }

    package_stats
}

def List parse_dependency_list(String dependencies) {
    List dependency_list = []
    dependencies.split(',').each {
        def (_, d) = (it =~ /\s*([-\w]+).*/)[0]
        dependency_list += d
    }
    dependency_list
}

get("/package-stats") {
    new StreamingMarkupBuilder().bind {
        html {
            head { title('Packages') }
            body {
                h1 'Packages'
                table {
                    def packageStats = get_package_stats()
                    h2 "${packageStats.size()} packages and subpackages"
                    def topLevelPackages = [] as Set
                    def total_build_section_line_count = 0
                    def packages_with_build_section = 0

                    def leaf_count = 0
                    def internal_count = 0
                    def internal_dependents_count = 0

                    def build_target_of_leaf_count = 0
                    def build_dependent_leaf_count = 0

                    packageStats.each { String package_id, Map stats ->
                        if (stats.hasBUILD && (stats.requires || stats.buildRequires || stats.requires_dependents || stats.buildRequires_dependents)) {
                            tr {
//                                td (stats.subpackage_id)
                                topLevelPackages += stats.package_id
                                total_build_section_line_count += stats.build_section_line_count
                                packages_with_build_section += 1

                                if (!(stats.requires_dependents || stats.buildRequires_dependents)) {
                                    leaf_count += 1
                                } else {
                                    internal_count += 1
                                    internal_dependents_count += stats.buildRequires_dependents.size() + stats.requires_dependents.size()
                                }

                                def build_dependent_leaves = stats.buildRequires_dependents.grep {
                                    !(packageStats[it].requires_dependents || packageStats[it].buildRequires_dependents)
                                }

                                if (build_dependent_leaves) {
                                    build_target_of_leaf_count += 1
                                    build_dependent_leaf_count += stats.buildRequires_dependents.size()
                                }

                                td {
                                    if (stats.package_id != stats.subpackage_id) {
                                        span(stats.subpackage_id)
                                        span(' ')
                                    }
                                    a(href:href_package(stats.package_id), stats.package_id)
                                }
                                td { span(stats.build_section_line_count) }
//                                td { span(stats.requires.size()) }
                                td { span(stats.requires.sort().join(', ')) }
//                                td { span(stats.buildRequires.size()) }
                                td { span(stats.buildRequires.sort().join(', ')) }
                                td { span(stats.requires_dependents.sort().join(', ')) }
                                td { span(stats.buildRequires_dependents.sort().join(', ')) }
                            }
                        }
                    }
                    h2 "${packages_with_build_section} packages with BUILD scripts in ${topLevelPackages.size()} top-level packages"
                    p "Average script line count is ${total_build_section_line_count/packages_with_build_section}"
                    p "leaf_count $leaf_count"
                    p "internal_count $internal_count"
                    p "internal_dependents_count $internal_dependents_count"
                    p "avg internal_dependents_count ${internal_dependents_count/internal_count}"

                    p "build_target_of_leaf_count $build_target_of_leaf_count"
                    p "build_dependent_leaf_count $build_dependent_leaf_count"
                    p "avg build_dependent_leaf_count ${build_dependent_leaf_count/build_target_of_leaf_count}"
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

    def (_X, mime_type, mime_subtype, file_path) = (req.pathInfo =~ file_path_pattern)[0]
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
Sentence  { background-color: rgb(220, 240, 220) ; border-spacing:1px 1px; border-style: none solid none solid }
Token     { background-color: rgb(220, 220, 240) ; border: 1px solid blue /* border-spacing:1px 1px; border-style: none dotted none dotted */ }
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

        def description_field = RPM_SPEC.Section.DESCRIPTION.token
        def spec = retriever.spec_for_package(package_id, true)
        def package_fields = spec['']

        def section = RPM_SPEC.token_to_section[description_field]
        def description = package_fields[section]

        def file_docs = []

        switch (params.file_query) {
            case Retriever.FILE_QUERY_ALL :
            case Retriever.FILE_QUERY_ALL_TEXT :
                file_docs = retriever.list_package_files(package_id, params.file_query == Retriever.FILE_QUERY_ALL_TEXT)
                // file_docs = file_docs.sort { it.get('file.shebang')+ '~' + it.get('file.mime_type') + '~' + it.get('file.name') }
                file_docs = file_docs.sort { a, b ->
                    a.get('file.shebang') <=> b.get('file.shebang') ?:
                        a.get('file.mime_type') <=> b.get('file.mime_type') ?:
                            a.get('file.name') <=> b.get('file.name') }
                break

            case Retriever.FILE_QUERY_README :
            case Retriever.FILE_QUERY_INSTALL :
                file_docs = retriever.list_package_files_with_name(package_id, params.file_query)
                break

            case Retriever.FILE_QUERY_SEARCH_FOR_DESCRIPTION :
                file_docs = retriever.list_package_files_like_description(package_id, description, true)
        }

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
                            td('Description')
                            td {
                                pre description
                            }
                        }
                        tr {
                            td('Interesting Terms')
                            td {
                                p(retriever.interestingTerms(description).join(', '))
                            }
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
                            td(params.file_query)
                            td {
                                a(href:href_package(package_id), 'No Files')
                                span(' ')
                                a(href:href_package(package_id, Retriever.FILE_QUERY_ALL), 'All Files')
                                span(' ')
                                a(href:href_package(package_id, Retriever.FILE_QUERY_ALL_TEXT), 'Text Files')
                                span(' ')
                                a(href:href_package(package_id, Retriever.FILE_QUERY_README), 'READMEs')
                                span(' ')
                                a(href:href_package(package_id, Retriever.FILE_QUERY_INSTALL), 'INSTALLs')
                                span(' ')
                                a(href:href_package(package_id, Retriever.FILE_QUERY_SEARCH_FOR_DESCRIPTION), 'Top Hits like Description')
                            }
                        }
                        tr {
                            td('Files')
                            td {
                                span(file_docs.size())
                            }
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
                                td(/*text_alignment(description, new File(doc.get('file.path')).text).score*/ '')
                                td {
                                    a(href:link, doc.get('file.build_path'))
                                }
                                td {
                                    a(href:href_process_file(doc.get('file.mime_type'), doc.get('file.path')), "process")
                                }
                                td {
                                    a(href:href_search(doc.get('file.path'), package_doc.get('package.spec_expanded'), RPM_SPEC.Section.DESCRIPTION.token ), "search")
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
    def spec = retriever.parse_spec_file(new File(spec_file_path))
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

MAX_SEQUENCE_LENGTH = 1000

def text_alignment(String text_a, String text_b)
{
    def a = new Sequence(text_a, analyzer, MAX_SEQUENCE_LENGTH)

    def b = new Sequence(text_b, analyzer)

//    SmithWatermanGotoh.align(a, b, new JaroWinklerSimilarity(), 10, 6, 10)
    SmithWatermanGotoh.align(a, b, new LevenshteinDistance(), 8, 4, 10)
}

get("/search")
{
    def file_path = params.file_path
    def spec_file_path = params.spec_file_path
    def subpackage_id = params.subpackage_id
    def search_field = params.field
    def slop = (params.slop ?: "0") as int

    def spec = retriever.parse_spec_file(new File(spec_file_path))
    def package_fields = spec['']

//    println "[$search_field]"
//    def keys = package_fields.keySet()
//    println keys
//    keys.each { println it ; if (it.equalsIgnoreCase(search_field)) println package_fields[it] }

    def section = RPM_SPEC.token_to_section[search_field]
    def search_string = package_fields[section]

    def alignment = text_alignment(search_string, new File(file_path).text)

    alignment.name1 = search_field
    alignment.name2 = file_path

    new StreamingMarkupBuilder().bind {
        html {
            head {
                title('Searching in ' + subpackage_id)
                style (type:"text/css", """
table { border : 1; margin : 1em ; outline-width : thin ; outline-style : dashed }
td { text-align : center; border-style : none dashed ; border-width : thin ; padding : 0 0.5em }
td.label { text-align : left; border-style : none; padding : 0 0.5em }
td.gap { background : LightGrey }
#.pair { margin : 1em }
""")
            }
            body {
                div('border-margin:1em') {
                    pre(alignment.summary)
                }
//                div('border-margin:1em') {
//                    pre(new Pair().format(alignment))
//                }
                div {
                    Token[] sequence1 = alignment.getSequence1();
                    Token[] sequence2 = alignment.getSequence2();
                    char[] markup = alignment.getMarkupLine();

                    int length = sequence1.length > sequence2.length ? sequence2.length : sequence1.length;

                    String name1 = Pair.adjustName(alignment.getName1());
                    String name2 = Pair.adjustName(alignment.getName2());

                    int position1 = 1 + alignment.getStart1();
                    int position2 = 1 + alignment.getStart2();

                    Token[] subsequence1;
                    Token[] subsequence2;
                    char[] submarkup;
                    int line;

                    Token c1, c2;

                    for (int i = 0; i * Pair.SEQUENCE_WIDTH < length; i++) {

                        line = ((i + 1) * Pair.SEQUENCE_WIDTH) < length ? (i + 1) * Pair.SEQUENCE_WIDTH: length;

                        subsequence1 = new Token[line - i * Pair.SEQUENCE_WIDTH];
                        subsequence2 = new Token[line - i * Pair.SEQUENCE_WIDTH];
                        submarkup = new char[line - i * Pair.SEQUENCE_WIDTH];

                        int k = 0
                        for (int j = i * Pair.SEQUENCE_WIDTH; j < line; j++) {
                            subsequence1[k] = sequence1[j];
                            subsequence2[k] = sequence2[j];
                            submarkup[k] = markup[j];
                            c1 = subsequence1[k];
                            c2 = subsequence2[k];
                            if (c1 == c2) {
                                position1++;
                                position2++;
                            } else if (c1 == Alignment.GAP) {
                                position2++;
                            } else if (c2 == Alignment.GAP) {
                                position1++;
                            } else {
                                position1++;
                                position2++;
                            }
                            k++;
                        }

                        div('class':'pair-div') {
                            table('class':'pair'/*, border:'1'*/) {
                                tr {
                                    td('class':'label', name1)
                                    for (Token t : subsequence1) {
                                        if (t == Alignment.GAP) {
                                            td('class':'gap')
                                        } else {
                                            td(t.toString())
                                        }
                                    }
                                }

                                tr {
                                    td('class':'label', )
                                    submarkup.each { td(it) }
                                }

                                tr {
                                    td('class':'label', name2)
                                    for (Token t : subsequence2) {
                                        if (t == Alignment.GAP) {
                                            td('class':'gap')
                                        } else {
                                            td(t.toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }.toString()
}

String href_package(String package_id)
{
    '/package/'+package_id
}

String href_package(String package_id, String file_query)
{
    "/package/$package_id?file_query=$file_query"
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

