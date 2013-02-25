#!/usr/bin/env groovy
package org.ifcx.readit.grader

import groovy.xml.MarkupBuilder

def environment = System.getenv().collect { it.key + '=' + it.value }

def report_config_file = new File(args.length > 0 ? args[0] : 'report.groovy')

def report_file = new File(report_config_file.parentFile, (report_config_file.name - ~/\.groovy$/) + '.html')
if (report_file.exists()) report_file.delete()

def report_config = evaluate(report_config_file)

File content_dir = report_config.content_dir

def tmpDir = File.createTempFile('run', '', content_dir)
tmpDir.delete()
tmpDir.mkdir()

println tmpDir

// Cyberduck quicklook has an annoying caching bug.
// Make the report in temp dir for this run then make a copy alongside the report config file.
def tmp_report_file = new File(tmpDir, (report_config_file.name - ~/\.groovy$/) + '.html')

tmp_report_file.withPrintWriter {
    new MarkupBuilder(it).html {
        h1('hw4 : ' + report_config.student_id)

        pre(tmpDir.absolutePath)

        def outFile = new File(tmpDir, 'r1acc.txt')
        def errFile = new File(tmpDir, 'r1err.txt')
        def sysFile = new File(tmpDir, 'r1sys.txt')

//[outFile, errFile, sysFile].each { if (it.exists()) it.delete() }

        h2 'build_kNN.sh'
// The format is: build kNN.sh training_data test_data k_val similarity_func sys_output > acc_file
        def executable = new File(report_config['build_kNN.sh'])

        if (!executable.canExecute()) {
            h3 'NOT EXECUTABLE'
            pre executable.path
            executable.setExecutable(true)
        }

        def command = [executable, '/dropbox/12-13/572/hw4/examples/train.vectors.txt', '/dropbox/12-13/572/hw4/examples/test.vectors.txt', 5, 2, sysFile.absolutePath]

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
            def sysLines = sysText.readLines().findAll()
            // Data lines for hw4 contain 8 columns and some are numbers.
            def isDataLine = { it.split(/\s+/).size() == 8 && it.split(/[.\d]+/).size() > 3 }
            def dataLines = sysLines.grep(isDataLine)
            def dataCount = dataLines.size()
            p "system output exists and contains ${sysLines.size()} non-empty lines of which $dataCount are data lines."
            p "data lines"
//            pre dataLines.take(5).join('\n')
            table(border:1) {
                dataLines.take(5).each { line -> tr { line.split(/\s+/).each { td(it) } } }
            }
            if (sysLines.size() > dataCount) {
                p "non-data lines"
                pre sysLines.grep { !isDataLine(it) }.take(5).join('\n')
            }
        } else {
            p "system output does not exist"
        }

        h3 "accuracy report:"
        pre outFile.text

    }
}

report_file << tmp_report_file.text
