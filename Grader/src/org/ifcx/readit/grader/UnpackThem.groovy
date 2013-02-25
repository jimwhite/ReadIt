#!/usr/bin/env groovy
// #!/usr/bin/env GROOVY_HOME=/home2/jimwhite/Projects/Groovy/groovy-1.8.6 /home2/jimwhite/Projects/Groovy/groovy-1.8.6/bin/groovy
package org.ifcx.readit.grader

println 'hello'

def submissions_dir = new File('.')

println submissions_dir.absolutePath

submissions_dir.eachDir { dir ->
    println "$dir ${dir.name}"

    def student_id = dir.name
    def content_dir = new File(dir, 'content')
    unpack_it(student_id, dir, content_dir)

    def report_file = new File(dir, 'report.groovy')
    report_file.withPrintWriter { report_writer ->
        locate_files(student_id, content_dir.absoluteFile, report_writer)
    }
    report_file.setExecutable(true)
    evaluate(report_file)
}

def unpack_it(String student_id, File dir, File content_dir)
{
    def environment = System.getenv().entrySet().grep { it.key =~ /PATH/ }.collect { it.key + '=' + it.value }

//    println environment

    def tar_files = dir.listFiles().grep { it.name =~ /(?i)\.(tar|tgz)/ }.sort {  -it.lastModified() }

    if (tar_files) {
//        println tar_files

        // Don't overwrite content if already there.
        if (content_dir.exists()) {
            println "Content already unpacked."
        } else {
            def unpack_dir = new File(dir, 'unpacked')
            if (unpack_dir.exists()) unpack_dir.delete()
            unpack_dir.mkdir()

            def command = ['tar', 'xf', tar_files[0].absolutePath]

//            println command.join(' ')
            def proc = command.execute(environment, unpack_dir)
            def stdout = new StringBuilder()
            def stderr = new StringBuilder()
            proc.consumeProcessOutput(stdout, stderr)
            proc.waitFor()
            if (stdout) println stdout
            if (stderr) {
            	println "ERROR:"
            	println stderr
            }
            if (proc.exitValue()) { println "Error: ${proc.exitValue()}" }

            def files = unpack_dir.listFiles()

            // Find the top-level directory of the tar file.  Skip levels with just a single directory.
            while (files.size() == 1 && files[0].isDirectory()) {
                unpack_dir = files[0]
                files = unpack_dir.listFiles()
            }

            if (files.size() < 1) {
                println "Can't unpack tar file or it is empty!"
            } else {
                // Move the top-level of the tar file to the 'content' directory.
                if (!unpack_dir.renameTo(content_dir)) {
                    println "RENAME FAILED!"
                }
            }
        }
    } else {
        println "NO TAR FILE!"
    }

}

def locate_files(String student_id, File content_dir, PrintWriter report)
{
    report << """#!/usr/bin/env groovy
// Student id $student_id
println "Student id: $student_id"

student_id='$student_id'
content_path='''$content_dir'''
content_dir=new File(content_path)
report_config=[student_id:student_id, content_path:content_path /*, content_dir:content_dir*/]
"""

    def file_list = []
    content_dir.eachFileRecurse { file_list << it }

    file_list.sort(true) { -it.lastModified() }

    ["build_kNN.sh"].each { filename ->
        File best_match = null
        def matches = []

        file_list.each { File f ->
            if (f.name == filename && !best_match) best_match = f
            if (f.name.equalsIgnoreCase(filename)) matches << f
        }

        if (!best_match && matches) best_match = matches[0]

        if (best_match && !best_match.canExecute()) {
            if (matches.find { it.canExecute() }) {
                report << "println 'Best match not executable: $best_match, using first executable near match.'"
                best_match = matches.find { it.canExecute() }
            } else {
                report << "println 'Not executable: ${best_match}.  Made it executable.'"
                best_match.setExecutable(true)
            }
        }

        if (matches.size() > 1) { report << "println '${matches.size()} matches for $filename'" }

        report << """
// $matches
// {matches.parentFile}
// {matches.collect { new File(it.parentFile.absoluteFile, it.absolutePath)}}
report_config['$filename']= ${best_match ? "'''${best_match}'''" : null }
"""
    }

    report << """
println report_config

"""

	report << """
if (args.size() > 0 && args[0] == '-runit') {
     def environment = System.getenv().collect { it.key + '=' + it.value }
     def tmpDir = new File(content_dir, 'zzjimwhite')
     tmpDir.mkdir()
     def outFile = new File(tmpDir, 'r1acc.txt')
     def errFile = new File(tmpDir, 'r1err.txt')
     def sysFile = new File(tmpDir, 'r1sys.txt')
     [outFile, errFile, sysFile].each { if (it.exists()) it.delete() }
     // The format is: build kNN.sh training_data test_data k_val similarity_func sys_output > acc_file
     def command = [report_config['build_kNN.sh'],  '/dropbox/12-13/572/hw4/examples/train.vectors.txt', '/dropbox/12-13/572/hw4/examples/test.vectors.txt', 5, 2, sysFile.absolutePath]
     println command.join(' ')
     outFile.withOutputStream { stdout ->
        errFile.withOutputStream { stderr ->
           def proc = command.execute(environment, content_dir)
           proc.consumeProcessOutput(stdout, stderr)
           proc.waitFor()
           if (proc.exitValue()) { println "Error: \${proc.exitValue()}" }
        }
     }
     if (sysFile.exists()) {
         def sysText = sysFile.text
         def sysLines = sysText.readLines()
         def dataLines = sysLines.grep { it.split(/\\s+/).size() == 8 && it.split(/[.\\d]+/).size() > 3 }
         def dataCount = dataLines.size()
         println "system output exists and contains \${sysLines.size()} lines of which \$dataCount are data lines."
         println dataLines.take(5).join('\\n')
     } else {
         println "system output does not exist"
     }
     def errText = errFile.text
     if (errText) {
         println "stderr"
         println errText
     }
     println "accuracy report:"
     println outFile.text
   
}
"""
}
