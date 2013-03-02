#!/usr/bin/env groovy
package org.ifcx.readit.grader

import groovy.xml.MarkupBuilder

def environment = System.getenv().collect { it.key + '=' + it.value }

def do_calculations = true

if (args.length > 0 && args[0].startsWith('-')) {
    do_calculations = args[0] == "-run"
    args = args.tail()
}

def report_config_file = new File(args.length > 0 ? args[0] : 'report.groovy')

def report_dir = report_config_file.parentFile

def report_file = new File(report_dir, (report_config_file.name - ~/\.groovy$/) + '.html')
if (report_file.exists()) report_file.delete()

def report_config = evaluate(report_config_file)

def tmpDir = File.createTempFile('run', '', report_dir)
tmpDir.delete()
tmpDir.mkdir()
println tmpDir

def expected_content_dir = new File('/home2/ling572_00/hw5/_key/content')

whitespace_pattern = ~/\s+/
number_pattern_str = /[+-]?(?:(?:\d+(?:\.\d*))|(?:\.\d+))[-+Ee.\d]*/
number_pattern = ~number_pattern_str

// Cyberduck quicklook has an annoying caching bug.
// Make the report in temp dir for this run then make a copy alongside the report config file.
def tmp_report_file = new File(tmpDir, (report_config_file.name - ~/\.groovy$/) + '.html')

tmp_report_file.withPrintWriter {
    new MarkupBuilder(it).html {
        h1(report_config.student_id)

        pre tmpDir.absolutePath

        pre report_config_file.text
        br()

        def hw4q1 = { File executable ->
            def executableModified = new File(executable.parentFile, executable.name + "-MODIFIED")
            if (!executable.canExecute()) {
                executableModified << "MADE EXECUTABLE\n"
                executable.setExecutable(true)
            }

            def outFile = new File(tmpDir, 'q1acc.txt')
            def errFile = new File(tmpDir, 'q1err.txt')
            def sysFile = new File(tmpDir, 'q1sys.txt')

            // The format is: build kNN.sh training_data test_data k_val similarity_func sys_output > acc_file
            // condor_run perl /dropbox/12-13/572/hw4/solution/build_kNN_svmlight.pl /dropbox/12-13/572/hw4/examples/train.vectors.txt /dropbox/12-13/572/hw4/examples/test.vectors.txt 5 1 zsys_5_1.txt >zacc_5_1.txt
            // condor_run perl /dropbox/12-13/572/hw4/solution/build_kNN_svmlight.pl /dropbox/12-13/572/hw4/examples/train2.vectors.txt /dropbox/12-13/572/hw4/examples/test2.vectors.txt 5 1 bzsys_5_1.txt >bzacc_5_1.txt
            def command = [executable, '/dropbox/12-13/572/hw4/examples/train.vectors.txt', '/dropbox/12-13/572/hw4/examples/test.vectors.txt', 5, 2, sysFile.absolutePath]

            p {
                pre(command.join(' '))
            }

            if (executableModified.exists()) {
                h3 'Executable Modified'
                pre executable.path
                pre executableModified.text
            }

            outFile.withOutputStream { stdout ->
                errFile.withOutputStream { stderr ->
                    def proc = command.execute(environment, executable.parentFile)
                    proc.consumeProcessOutput(stdout, stderr)
                    proc.waitFor()
                    if (proc.exitValue()) {
                        h3 'Error'
                        p "exitValue: ${proc.exitValue()}"
                    }
                }
            }

            def errText = errFile.text
            if (errText) {
                h3 "stderr"
                pre errText
            }

            h3 'System Output'
            if (sysFile.exists()) {
                def sysText = sysFile.text
                // Turn into a list of non-empty lines.
                def sysLines = sysText.readLines().findAll()
                // Data lines for hw4 contain 8 columns and some are numbers.
                def isDataLine = { whitespace_pattern.split(it).size() == 8 && number_pattern.split(it).size() > 3 }
                def dataLines = sysLines.grep(isDataLine)
                def dataCount = dataLines.size()
                p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
                p "data lines"
//            pre dataLines.take(5).join('\n')
                table(border:1) {
                    dataLines.take(10).each { line -> tr { whitespace_pattern.split(line).each { td(it) } } }
                }
                if (sysLines.size() > dataCount) {
                    p "non-data lines"
                    pre sysLines.grep { !isDataLine(it) }.take(10).join('\n')
                }
            } else {
                p "system output does not exist"
            }

            h3 "accuracy report:"
            pre outFile.text
        }

        def hw4q3 = { File executable ->
            def executableModified = new File(executable.parentFile, executable.name + "-MODIFIED")
            if (!executable.canExecute()) {
                executableModified << "MADE EXECUTABLE\n"
                // Make it executable by anybody.
                executable.setExecutable(true, false)
            }

            if (executableModified.exists()) {
                h3 'Executable Modified'
                pre executable.path
                pre executableModified.text
            }

            def inFile = new File('/dropbox/12-13/572/hw4/examples/train.vectors.txt')
            def outFile = new File(tmpDir, 'q3out.txt')
            def errFile = new File(tmpDir, 'q3err.txt')
//            def sysFile = new File(tmpDir, 'q3sys.txt')

            // The format is: rank_feat_by_chi_square.sh <input_file > output_file
            def command = [executable, inFile.absolutePath]

            p {
                pre(command.join(' '))
            }

            outFile.withOutputStream { stdout ->
                errFile.withOutputStream { stderr ->
                    def proc = command.execute(environment, executable.parentFile)
                    proc.withWriter { stdin -> inFile.withReader { try { stdin << it } catch (IOException ex) { p(ex.getMessage()) } } }
                    proc.consumeProcessOutput(stdout, stderr)
                    proc.waitFor()
                    if (proc.exitValue()) {
                        h3 'Error'
                        p "exitValue: ${proc.exitValue()}"
                    }
                }
            }

            def errText = errFile.text
            if (errText) {
                h3 "stderr"
                pre errText
            }

            h3 'System Output'
            if (outFile.exists()) {
                def sysText = outFile.text
                // Turn into a list of non-empty lines.
                def sysLines = sysText.readLines().collect { it.trim() }.findAll()
                // Data lines for hw4 contain 3 columns and some are numbers.
                // The output file has the format “featName score docFreq”.
                // The score is the chi-square score for the feature; docFreq is the number of documents that the feature occurs in.
                // The lines are sorted by χ2 score in descending order.
                def isDataLine = { whitespace_pattern.split(it).size() == 3 && number_pattern.split(it).size() > 1 }
                def dataLines = sysLines.grep(isDataLine)
                def dataCount = dataLines.size()
                p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
                p "data lines"
                table(border:1) {
                    dataLines.take(10).each { line -> tr { whitespace_pattern.split(line).each { td(it) } } }
                }
                if (sysLines.size() > dataCount) {
                    p "non-data lines"
                    pre sysLines.grep { !isDataLine(it) }.take(10).join('\n')
                }
            } else {
                p "system output does not exist"
            }

        }

        def classifier_expectation = { File sysFile, File expected ->
            def sysText = sysFile.text
            // Turn into a list of non-empty lines.
            def sysLines = sysText.readLines().collect { it.trim() }.findAll()
            // Classifier output data lines for contain 8 columns and some are numbers.
            def isDataLine = { whitespace_pattern.split(it).size() == 8 && number_pattern.split(it).size() > 3 }
            def observed_data = sysLines.grep(isDataLine)
            def dataCount = observed_data.size()
            p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
            p "data lines"
            table(border:1) {
                observed_data.take(10).each { line -> tr { whitespace_pattern.split(line).each { td(it) } } }
            }

            def expText = expected.text
            def expLines = expText.readLines().collect { it.trim() }.findAll()
            def expected_data = expLines.grep(isDataLine)

            p "expectation"
            if ( observed_data.size() != expected_data.size()) {
                p "Wrong number of data lines in file.  Expected ${expected_data.size()} and got ${ observed_data.size()}."
            } else {
                p "Got expected number of data lines in file (${expected_data.size()})."
            }

            List<Integer> numeric_fields = [3, 5, 7]
            List<Integer> nonnumeric_fields = (1..8) - numeric_fields

//            observed_data.sort(true)
//            expected_data.sort(true)

            def e = parse_data(numeric_fields, expected_data)
            def o = parse_data(numeric_fields, observed_data)

            int n = Math.min( observed_data.size(), expected_data.size())

            def e_mean = new double[8]
            def o_mean = new double[8]

//            println "e[0]"
//            e[0].each { println "'$it' ${it.class}" }
//            println "o[0]"
//            o[0].each { println "'$it' ${it.class}" }

            numeric_fields.each { j -> n.times { i -> e_mean[j] += e[i][j] } ; e_mean[j] /= n }
            numeric_fields.each { j -> n.times { i -> o_mean[j] += o[i][j] } ; o_mean[j] /= n }

            def e_var = new double[8]
            def o_var = new double[8]

            numeric_fields.each { j -> n.times { i -> e_var[j] += (e[i][j] - e_mean[j]) ** 2 } }
            numeric_fields.each { j -> n.times { i -> o_var[j] += (e[i][j] - o_mean[j]) ** 2 } }

            def variance = [0.0e0] * 8
            def distances = new Object[n]

            n.times { i ->
                def d0 = variance.sum()
                nonnumeric_fields.each { if (o[i][it] != e[i][it]) variance[it] += 1 }
                numeric_fields.each { j -> variance[j] += ((o[i][j] - o_mean[j]) - (e[i][j] - e_mean[j])) ** 2 }
                distances[i] = [variance.sum() - d0, i]
            }

            numeric_fields.each { j -> variance[j] /= Math.sqrt(e_var[j]) * Math.sqrt(o_var[j]) }

            if (variance.every { it < 0.0001 }) { p "Data values as expected." }

            table(border:1) {
                tr { variance.each { td(it.toString()) }}
            }

            br()

            distances.sort(true) { a, b -> b[0] <=> a[0] }

            table(border:1) {
                distances.take(10).each { d, i ->
                    tr { td('E') ; e[i].each { td(it.toString()) } }
                    tr { td('O') ; o[i].eachWithIndex { v, j -> td(v.toString()) } }
                }
            }

            if (sysLines.size() > dataCount) {
                p "non-data lines"
                pre sysLines.grep { !isDataLine(it) }.take(10).join('\n')
            }
        }

        def maxent_classify = { File executable, File expected ->
            def executableModified = new File(executable.parentFile, executable.name + "-MODIFIED")
            if (!executable.canExecute()) {
                executableModified << "MADE EXECUTABLE\n"
                // Make it executable by anybody.
                executable.setExecutable(true, false)
            }

            if (executableModified.exists()) {
                h3 'Executable Modified'
                pre executable.path
                pre executableModified.text
            }

            // maxent_classify.sh /dropbox/12-13/572/hw5/examples/test2.vectors.txt q1/m1.txt q2/res >q2/acc

//            def inFile = new File('/dropbox/12-13/572/hw5/examples/train.vectors.txt')
            def outFile = new File(tmpDir, 'q2out.txt')
            def errFile = new File(tmpDir, 'q2err.txt')
            def sysFile = new File(tmpDir, 'q2res.txt')

            // The format is: rank_feat_by_chi_square.sh <input_file > output_file
            def command = [executable, '/dropbox/12-13/572/hw5/examples/test2.vectors.txt', report_config['m1.txt']?.absolutePath, sysFile.absolutePath]

            p {
                pre(command.join(' '))
            }

            outFile.withOutputStream { stdout ->
                errFile.withOutputStream { stderr ->
                    def proc = command.execute(environment, executable.parentFile)
                    proc.consumeProcessOutput(stdout, stderr)
                    proc.waitFor()
                    if (proc.exitValue()) {
                        h3 'Error'
                        p "exitValue: ${proc.exitValue()}"
                    }
                }
            }

            def errText = errFile.text
            if (errText) {
                h3 "stderr"
                pre errText
            }

            h3 'System Output'
            if (sysFile.exists()) {
                classifier_expectation(sysFile  , expected)
            } else {
                p "system output does not exist"
            }

            h3 "accuracy report:"
            pre outFile.text
        }

        def expectation_expectation = { File sysFile, File expected ->
            def sysText = sysFile.text
            // Turn into a list of non-empty lines.
            def sysLines = sysText.readLines().collect { it.trim() }.findAll()
            // Classifier output data lines for contain 4 columns and some are numbers.
            def isDataLine = { whitespace_pattern.split(it).size() == 4 && number_pattern.split(it).size() > 1 }
            def observed_data = sysLines.grep(isDataLine)
            observed_data = observed_data.sort()
            def dataCount = observed_data.size()

            p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
//                p "data lines"
//                table(border:1) {
//                    dataLines.take(10).each { line -> tr { line.split(/\s+/).each { td(it) } } }
//                    dataLines.reverse().take(10).each { line -> tr { line.split(/\s+/).each { td(it) } } }
//                }

            def expText = expected.text
            def expLines = expText.readLines().collect { it.trim() }.findAll()
            def expected_data = expLines.grep(isDataLine)

            List<Integer> numeric_fields = [2, 3]
            List<Integer> nonnumeric_fields = (0..4) - numeric_fields

            def e = parse_data(numeric_fields, expected_data)
            def o = parse_data(numeric_fields, observed_data)

            //FIXME: SUPER unhelpful error message if your closure gives collectEntries a list with wrong number of elements.
            //Assumes anything that isn't a two element list is a Map.Entry and tries to do getKey on it.
//            def expectation = (e as List).collect { r ->
//                def (c, f, ev, ac) = r
//                def kv = [to_key(c, f), ev, ac]
//                kv
//            }.collectEntries()
//
//            def observation = (o as List).collect { r ->
//                def (c, f, ev, ac) = r
//                def kv = [to_key(c, f), ev, ac]
//                kv
//            }.collectEntries()

            def expectation = e.collectEntries { c, f, ev, ac -> [to_key(c, f), [ev, ac]] }
            def observation = o.collectEntries { c, f, ev, ac -> [to_key(c, f), [ev, ac]] }

//            def nzo = o.grep { it.slice(numeric_fields).every() }
//            def nze = e.grep { it.slice(numeric_fields).every() }
            def nzo = o.grep { it[2, 3].every() }
            def nze = e.grep { it[2, 3].every() }

            p "expectation"
            if ( nzo.size() != nze.size()) {
                p "Wrong number of non-zero data lines in file.  Expected ${nze.size()} and got ${nzo.size()}."

                p "some missing values"

                //BUG? this either gets stuck or is crazy slow.
//                def missing_keys = (expectation.keySet() - observation.keySet()) as List
//                missing_keys = missing_keys.grep { expectation[it][0] || expectation[it][1] }

                def missing_e = nze.grep { c, f, ev, ac ->
                    def c_f = to_key(c, f)
                    ev && ac && (!observation.containsKey(c_f) || !observation[c_f][0] && !observation[c_f][1])
                }

                table(border:1) {
//                    missing_keys.take(10).each { c_f -> tr { td(c_f) ; expectation[c_f].each { td(it as String) } } }
                    missing_e.take(15).each { r -> tr { r.each { td(it as String) } } }
                    missing_e.reverse().take(15).each { r -> tr { r.each { td(it as String) } } }
//                    missing_e.reverse().take(10).each { c_f -> tr { td(c_f) ; td(expectation[c_f][0] as String) ; td(expectation[c_f][1] as String) } }
                }
            } else {
                p "Got expected number of non-zero data lines in file (${nze.size()})."
            }
            p "Total expected rows ${e.size()} and observed rows ${o.size()}"

            p "data"
            table(border:1) {
                nzo.take(15).each { r -> tr { r.each { td(it as String) } } }
                nzo.reverse().take(15).each { r -> tr { r.each { td(it as String) } } }
            }

            def variance = new double[4]
            def distances = new Object[o.size()]

            o.eachWithIndex { r, i ->
                def (c, f, ev, ac) = r

                double[] m = expectation[to_key(c, f)]
                if (m) {
                    variance[2] += (ev - m[0]) ** 2
                    variance[3] += (ac - m[1]) ** 2

                    distances[i] = [((ev - m[0]) ** 2) + ((ac - m[1]) ** 2), i]
                } else {
                    variance[1] += 1
                }
            }

            p ((variance.every { it < 0.0001 }) ? "Data values as expected." : "Data values differ more than expected.")

            table(border:1) {
                tr { variance.each { td(it.toString()) }}
            }

            p "biggest differences"

            distances = distances.grep { it != null }.sort { a, b -> b[0] <=> a[0] }

            table(border:1) {
                distances.take(10).each { d, i ->
                    def (c, f, ev, ac) = o[i]
                    tr { td('O') ; o[i].eachWithIndex { v, j -> td(v.toString()) } }
                    tr { td('E') ; td(colspan:2); (expectation[to_key(c, f)] as List).each { td(it.toString()) } }
                }
            }

            if (sysLines.size() > dataCount) {
                p "non-data lines"
                pre sysLines.grep { !isDataLine(it) }.take(10).join('\n')
            }
        }

        def calc_exp = { String title, String name, File expected, File model_file = null ->
            h2 title

            File executable = report_config[name]

            if (!executable) {
                h3 "No executable for $name"
                return
            }

            def executableModified = new File(executable.parentFile, executable.name + "-MODIFIED")
            if (!executable.canExecute()) {
                executableModified << "MADE EXECUTABLE\n"
                // Make it executable by anybody.
                executable.setExecutable(true, false)
            }

            if (executableModified.exists()) {
                h3 'Executable Modified'
                pre executable.path
                pre executableModified.text
            }

            // calc_emp_exp.sh /dropbox/12-13/572/hw5/examples/train2.vectors.txt q3/emp_count

            def outFile = new File(tmpDir, title+'_out.txt')
            def errFile = new File(tmpDir, title+'_err.txt')
            def sysFile = new File(tmpDir, title+'_count.txt')

            def command = [executable, '/dropbox/12-13/572/hw5/examples/train2.vectors.txt', sysFile.absolutePath]
            if (model_file) command << model_file

            p {
                pre(command.join(' '))
            }

            outFile.withOutputStream { stdout ->
                errFile.withOutputStream { stderr ->
                    def proc = command.execute(environment, executable.parentFile)
                    proc.consumeProcessOutput(stdout, stderr)
                    proc.waitFor()
                    if (proc.exitValue()) {
                        h3 'Error'
                        p "exitValue: ${proc.exitValue()}"
                    }
                }
            }

            def errText = errFile.text
            if (errText) {
                h3 "stderr"
                pre errText
            }

            def outText = outFile.text
            if (outText) {
                h3 "stdout"
                pre outText
            }

            h3 'System Output'
            if (sysFile.exists()) {

                expectation_expectation(sysFile, expected)

            } else {
                p "system output does not exist"
            }

        }

        def text_file = { String name ->
            File file = report_config[name]

            h3 name
            if (file) {
                def lines = file.readLines()
                p "$name present as '.${file.path - report_config.content_dir.path}' and contains ${lines.size()} lines"
                pre lines.take(10).join('\n')
            } else {
                p "No file for $name"
            }

        }

        def binary_file = { String name ->
            File file = report_config[name]

            h3 name
            if (file) {
                p "$name present as '.${file.path - report_config.content_dir.path}' and contains ${file.size()} bytes"
            } else {
                p "No file for $name"
            }

        }

        h2 "q1"
        binary_file('m1')
        text_file('m1.txt')

        h2 'q2/res'
        File q2_res = report_config['res']
        if (q2_res) {
            classifier_expectation(q2_res, new File(expected_content_dir, 'q2/res'))
        } else {
            p "No q2/res file"
        }

        if (do_calculations) {
            h2 "q2 - maxent_classify.sh"
            File q2_executable = report_config['maxent_classify.sh']
            if (q2_executable) {
                maxent_classify q2_executable, new File(expected_content_dir, 'q2/res')
            } else {
                p "No executable for q2"
            }

            calc_exp('q3', 'calc_emp_exp.sh', new File(expected_content_dir, 'q3/emp_count'))

            calc_exp('q4d', 'calc_model_exp.sh', new File(expected_content_dir, 'q4/model_count'))

            calc_exp('q4f', 'calc_model_exp.sh', new File(expected_content_dir, 'q4/model_count2'), new File(expected_content_dir, 'q1/m1.txt'))
        }

        [['emp_count', 'q3/emp_count'], ['model_count', 'q4/model_count'], ['model_count2', 'q4/model_count2']].each { String kt, String file_p ->
            h2 file_p
            File f = report_config[kt]
            if (f) {
                expectation_expectation(f, new File(expected_content_dir, file_p))
            } else {
                p "No $file_p file"
            }
        }


//        h2 "q1 - build_kNN.sh"
//        File q1_executable = report_config['build_kNN.sh']
//        if (q1_executable) {
//            q1 q1_executable
//        } else {
//            p "No executable for q1"
//        }
//
//        h2 "q3 - rank_feat_by_chi_square.sh"
//        File q3_executable = report_config['rank_feat_by_chi_square.sh']
//        if (q3_executable) {
//            q3 q3_executable
//        } else {
//            p "No executable for q3"
//        }

        br()
        hr()
    }

}

private String to_key(c, f) { c + '|' + f }

Object[] parse_data(List<Integer> numeric_fields, List<String> data) {
    def x = new Object[data.size()]

    data.eachWithIndex { String entry, int i ->
        x[i] = whitespace_pattern.split(data[i]) as List

        numeric_fields.each { j ->
            try {
                x[i][j] = x[i][j] as Double
            } catch (NumberFormatException ex) {
                if (i == 0) println "x[$i][$j] is bad '${x[i][j]}'"
                x[i][j] = 0.0e0
            }
        }

//        if (i == 0) {
//            println "x[0]"
//            x[0].each { println "'$it' ${it.class}" }
//        }
    }

//    println "x[0]"
//    x[0].each { println "'$it' ${it.class}" }

    x
}

// report_file << tmp_report_file.text

