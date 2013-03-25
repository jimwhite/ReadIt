#!/usr/bin/env groovy
// #!/usr/bin/env GROOVY_HOME=/home2/jimwhite/Projects/Groovy/groovy-1.8.6 /home2/jimwhite/Projects/Groovy/groovy-1.8.6/bin/groovy
package org.ifcx.readit.grader

// hw4:  ~/ReadIt/Grader/src/org/ifcx/readit/grader/UnpackThem.groovy build_kNN.sh rank_feat_by_chi_square.sh
// hw5:  ~/ReadIt/Grader/src/org/ifcx/readit/grader/UnpackThem.groovy maxent_classify.sh calc_emp_exp.sh calc_model_exp.sh

def submissions_dir = new File('.')

def homework = args[0].toLowerCase()

enum LocationType { EXECUTABLE, TEXT, BINARY }

def locations = []

switch (homework) {
    case 'hw5' :
        locations << [name:'maxent_classify.sh', type:LocationType.EXECUTABLE]
        locations << [name:'calc_emp_exp.sh', type:LocationType.EXECUTABLE]
        locations << [name:'calc_model_exp.sh', type:LocationType.EXECUTABLE]
        locations << [name:'m1', dir:'q1', type:LocationType.BINARY]
        locations << [name:'m1.txt', dir:'q1', type:LocationType.TEXT]
        locations << [name:'acc', dir:'q2', type:LocationType.BINARY]
        locations << [name:'res', dir:'q2', type:LocationType.TEXT]
        locations << [name:'emp_count', dir:'q3', type:LocationType.TEXT]
        locations << [name:'model_count', dir:'q4', type:LocationType.BINARY]
        locations << [name:'model_count2', dir:'q4', type:LocationType.TEXT]
        break
    case 'hw6' :
        locations << [name:'beamsearch_maxent.sh', type:LocationType.EXECUTABLE]
        break
    case 'hw7' :
        locations << [name:'svm_classify.sh', type:LocationType.EXECUTABLE]
        locations << [name:'model.1', dir:'q1', type:LocationType.TEXT]
        locations << [name:'model.2', dir:'q1', type:LocationType.TEXT]
        locations << [name:'model.3', dir:'q1', type:LocationType.TEXT]
        locations << [name:'model.4', dir:'q1', type:LocationType.TEXT]
        locations << [name:'model.5', dir:'q1', type:LocationType.TEXT]
        locations << [name:'sys.1', dir:'q2', type:LocationType.TEXT]
        locations << [name:'sys.2', dir:'q2', type:LocationType.TEXT]
        locations << [name:'sys.3', dir:'q2', type:LocationType.TEXT]
        locations << [name:'sys.4', dir:'q2', type:LocationType.TEXT]
        locations << [name:'sys.5', dir:'q2', type:LocationType.TEXT]
        break
    default :
        System.err.println "Bad homework id $homework"
        System.exit(1)
}

println submissions_dir.absolutePath

new File(submissions_dir, 'run_all.sh').withPrintWriter { run_script ->
    submissions_dir.eachDir { dir ->
        println "$dir ${dir.name}"

        def student_id = dir.name
        def content_dir = new File(dir, 'content')
        unpack_it(student_id, dir, content_dir)

        if (content_dir.exists()) {
            def report_file = new File(dir, 'report.groovy')
            report_file.withPrintWriter { report_writer ->
                locate_files(student_id, content_dir.absoluteFile, report_writer, locations)
            }
            report_file.setExecutable(true)
            evaluate(report_file)

            run_script.println "condor_run ~/ReadIt/Grader/src/org/ifcx/readit/grader/RunIt.groovy $report_file &"
        }
    }
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

def locate_files(String student_id, File content_dir, PrintWriter report, List locations)
{
    report << """// Homework Report Config

student_id='$student_id'
content_path='$content_dir'
content_dir=new File(content_path)
report_config=[student_id:student_id, content_path:content_path, content_dir:content_dir]
"""

//    report << """#!/usr/bin/env groovy
//// Student id $student_id
//println "Student id: $student_id"
//
//student_id='$student_id'
//content_path='''$content_dir'''
//content_dir=new File(content_path)
//report_config=[student_id:student_id, content_path:content_path, content_dir:content_dir]
//"""

    def file_list = []
    content_dir.eachFileRecurse { file_list << it }
    file_list.sort(true) { -it.lastModified() }

    locations.grep { it.type == LocationType.EXECUTABLE }.each {
        String filename = it.name

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
//            } else {
//                report << "println 'Not executable: ${best_match}.  Made it executable.'"
//                best_match.setExecutable(true)
            }
        }

        if (matches.size() > 1) report.println "println '${matches.size()} matches for $filename'"

        matches.each { report.println "${it == best_match ? '' : '// '}report_config['$filename']=new File('${it.absolutePath}') ${it.canExecute() ? '' : '// NOT EXECUTABLE' }"  }
    }

    locations.grep { it.type in [LocationType.TEXT, LocationType.BINARY] }.each {
        String filename = it.name
        String dir = it.dir

        File best_match = null
        def matches = []

        file_list.each { File f ->
            if (f.name == filename && (!dir || dir.equalsIgnoreCase(f.parentFile.name)) && !best_match) best_match = f
            if (f.name.equalsIgnoreCase(filename)) matches << f
        }

        if (!best_match && matches) best_match = matches[0]

        if (matches.size() > 1) report.println "println '${matches.size()} matches for $filename'"

        matches.each { report.println "${it == best_match ? '' : '// '}report_config['$filename']=new File('${it.absolutePath}')"  }

        if (!matches) { report.println "// No matches for $filename" }
    }

    report.println()
    report.println "return report_config"

}
