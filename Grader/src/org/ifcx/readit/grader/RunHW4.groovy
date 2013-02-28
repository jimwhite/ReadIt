#!/usr/bin/env groovy
package org.ifcx.readit.grader

import groovy.xml.MarkupBuilder

def environment = System.getenv().collect { it.key + '=' + it.value }

def report_config_file = new File(args.length > 0 ? args[0] : 'report.groovy')

def report_dir = report_config_file.parentFile

def report_file = new File(report_dir, (report_config_file.name - ~/\.groovy$/) + '.html')
if (report_file.exists()) report_file.delete()

//def outSW = new StringWriter()
//out = new PrintWriter(outSW)
def report_config = evaluate(report_config_file)

def tmpDir = File.createTempFile('run', '', report_dir)
tmpDir.delete()
tmpDir.mkdir()

println tmpDir

//out.flush()

// Cyberduck quicklook has an annoying caching bug.
// Make the report in temp dir for this run then make a copy alongside the report config file.
def tmp_report_file = new File(tmpDir, (report_config_file.name - ~/\.groovy$/) + '.html')

tmp_report_file.withPrintWriter {
    new MarkupBuilder(it).html {
        h1('hw4 : ' + report_config.student_id)

        pre tmpDir.absolutePath

//        pre outSW.toString()

        pre report_config_file.text
        br()

        def q1 = { File executable ->
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

        def q3 = { File executable ->
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

        h2 "q1 - build_kNN.sh"
        File q1_executable = report_config['build_kNN.sh']
        if (q1_executable) {
            q1 q1_executable
        } else {
            p "No executable for q1"
        }

        h2 "q3 - rank_feat_by_chi_square.sh"
        File q3_executable = report_config['rank_feat_by_chi_square.sh']
        if (q3_executable) {
            q3 q3_executable
        } else {
            p "No executable for q3"
        }
    }
}

report_file << tmp_report_file.text
