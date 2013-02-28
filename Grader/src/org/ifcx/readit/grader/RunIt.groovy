#!/usr/bin/env groovy
package org.ifcx.readit.grader

import groovy.xml.MarkupBuilder

def environment = System.getenv().collect { it.key + '=' + it.value }

def report_config_file = new File(args.length > 0 ? args[0] : 'report.groovy')

def report_dir = report_config_file.parentFile

def report_file = new File(report_dir, (report_config_file.name - ~/\.groovy$/) + '.html')
if (report_file.exists()) report_file.delete()

def report_config = evaluate(report_config_file)

def tmpDir = File.createTempFile('run', '', report_dir)
tmpDir.delete()
tmpDir.mkdir()
println tmpDir

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
                def isDataLine = { it.split(/\s+/).size() == 8 && it.split(/[.\d]+/).size() > 3 }
                def dataLines = sysLines.grep(isDataLine)
                def dataCount = dataLines.size()
                p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
                p "data lines"
//            pre dataLines.take(5).join('\n')
                table(border:1) {
                    dataLines.take(10).each { line -> tr { line.split(/\s+/).each { td(it) } } }
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
                def isDataLine = { it.split(/\s+/).size() == 3 && it.split(/[.\d]+/).size() > 1 }
                def dataLines = sysLines.grep(isDataLine)
                def dataCount = dataLines.size()
                p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
                p "data lines"
                table(border:1) {
                    dataLines.take(10).each { line -> tr { line.split(/\s+/).each { td(it) } } }
                }
                if (sysLines.size() > dataCount) {
                    p "non-data lines"
                    pre sysLines.grep { !isDataLine(it) }.take(10).join('\n')
                }
            } else {
                p "system output does not exist"
            }

        }

        def maxent_classify = { File executable ->
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
            def outFile = new File(tmpDir, 'q3out.txt')
            def errFile = new File(tmpDir, 'q3err.txt')
            def sysFile = new File(tmpDir, 'q3res.txt')

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
                def sysText = sysFile.text
                // Turn into a list of non-empty lines.
                def sysLines = sysText.readLines().collect { it.trim() }.findAll()
                // Classifier output data lines for contain 8 columns and some are numbers.
                def isDataLine = { it.split(/\s+/).size() == 8 && it.split(/[.\d]+/).size() > 3 }
                def dataLines = sysLines.grep(isDataLine)
                def dataCount = dataLines.size()
                p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
                p "data lines"
                table(border:1) {
                    dataLines.take(10).each { line -> tr { line.split(/\s+/).each { td(it) } } }
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

        def calc_exp = { String title, String name, File model_file = null ->
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
                def sysText = sysFile.text
                // Turn into a list of non-empty lines.
                def sysLines = sysText.readLines().collect { it.trim() }.findAll()
                // Classifier output data lines for contain 4 columns and some are numbers.
                def isDataLine = { it.split(/\s+/).size() == 4 && it.split(/[-.eE\d]+/).size() > 1 }
                def dataLines = sysLines.grep(isDataLine)
                def dataCount = dataLines.size()
                p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
                p "data lines"
                table(border:1) {
                    dataLines.take(10).each { line -> tr { line.split(/\s+/).each { td(it) } } }
                }
                if (sysLines.size() > dataCount) {
                    p "non-data lines"
                    pre sysLines.grep { !isDataLine(it) }.take(10).join('\n')
                }
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

        h2 "q2 - maxent_classify.sh"
        File q2_executable = report_config['maxent_classify.sh']
        if (q2_executable) {
            maxent_classify q2_executable
        } else {
            p "No executable for q2"
        }

        calc_exp('q3', 'calc_emp_exp.sh')
        calc_exp('q4a', 'calc_model_exp.sh')

        def q1_m1_txt = report_config['m1.txt']
        if (q1_m1_txt) {
            calc_exp('q4b', 'calc_model_exp.sh', q1_m1_txt)
        } else {
            h3 'q4b'
            h2 "Not run because no model file included."
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

// report_file << tmp_report_file.text
