package org.ifcx.readit.grader

def submissions_dir = new File('.')

println submissions_dir.absolutePath

submissions_dir.eachDir { dir ->
    println "$dir ${dir.name}"

    unpack_it(dir)
}

def unpack_it(File dir)
{
    def environment = System.getenv().entrySet().grep { it.key =~ /PATH/ }.collect { it.key + '=' + it.value }

//    println environment

    def tar_files = dir.listFiles().grep { it.name =~ /(?i)\.(tar|tgz)/ }.sort { - it.lastModified }

    if (tar_files) {
        println tar_files
        def content_dir = new File(dir, 'content')
        // Don't overwrite content if already there.
        if (content_dir.exists()) {
            println "Content already unpacked."
        } else {
            def unpack_dir = new File(dir, 'unpacked')
            if (unpack_dir.exists()) unpack_dir.delete()
            unpack_dir.mkdir()
            def command = ['tar', 'xf', tar_files[0].absolutePath]
            println command.join(' ')
            def proc = command.execute(environment, unpack_dir)
            println (proc.text)
            if (proc.exitValue()) { println "Error: ${proc.exitValue()}"}
            while (unpack_dir.listFiles().size() == 1) {
                def files = unpack_dir.listFiles()
                if (files[0].isDirectory()) unpack_dir = files[0]
            }
            if (!unpack_dir.renameTo(content_dir)) {
                println "RENAME FAILED!"
            }
        }
    } else {
        println "NO TAR FILE!"
    }

}

//File find_tar_file(File dir)
//{
////    File tar_file = null
////    dir.eachFileMatch(~/(?i)\.(tar|tgz)/) {  if (!tar_file || (tar_file.lastModified < it.lastModified)) tar_file = it }
////    tar_file
//
//    dir.listFiles().grep { it.name =~ /(?i)\.(tar|tgz)/ }.sort { - it.lastModified }
//}
//
//def escape_path_for_quotes(File file)
//{
//    file.path.replaceAll(/([\\'])/, /\\$1/)
//}
//
//def escape_path_for_quotes(String path)
//{
//    path.replaceAll(/([\\'])/, /\\$1/)
//}
