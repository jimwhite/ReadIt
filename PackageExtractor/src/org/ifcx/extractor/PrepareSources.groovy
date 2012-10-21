package org.ifcx.extractor

LINUX_RPM = new File("/mnt/LINUX_RPM")

PACKAGE_INFO = new File(LINUX_RPM, "packages")

RPM_ROOT = new File(LINUX_RPM, "rpmbuild")
RPM_SPEC_DIR = new File(RPM_ROOT, "SPECS")
RPM_SOURCES_DIR = new File(RPM_ROOT, "SOURCES")
RPM_BUILD_DIR = new File(RPM_ROOT, "BUILD")

RPM_BUILD_DIR.mkdir()

// build_dir_paths = listFilesRecursively(RPM_BUILD_DIR)

RPM_SPEC_DIR.eachFile { File spec ->
    if (spec.name.endsWith(".spec")) {
        def package_name = spec.name - ~/\.spec$/
        def package_info_dir = new File(PACKAGE_INFO, package_name)
        if (!package_info_dir.exists()) {
//            def prep_source_dir = new File(package_info_dir, "PrepareSources")
            def prep_source_dir = package_info_dir
            prep_source_dir.mkdirs()
            def package_build_dir = new File(package_info_dir, "BUILD")

            String[] command = ["rpmbuild", "-bp", "--nodeps", spec]
            println command.join(' ')

            def proc = new ProcessBuilder(command).start()

            def cmd_output = new File(prep_source_dir, "output.txt")
            def cmd_error = new File(prep_source_dir, "error.txt")
            cmd_output.withWriter { output ->
                cmd_error.withWriter { error ->
                    proc.waitForProcessOutput(output, error)
                }
            }
            def exit_code = proc.exitValue()

//            def updated_build_dir_paths=listFilesRecursively(RPM_BUILD_DIR)
//            def new_build_dir_paths = updated_build_dir_paths - build_dir_paths
//            build_dir_paths = updated_build_dir_paths

            def build_files = new File(prep_source_dir, "build_files.txt")
            def file_types = new File(prep_source_dir, "file_types.txt")

//            sources.withPrintWriter { writer ->
//                new_build_dir_paths.each { writer.println it }
//            }

            find_all_files(RPM_ROOT, RPM_BUILD_DIR.name, build_files)
            file_mime_types(RPM_ROOT, build_files, file_types)

            def build_files_count = build_files.readLines().size()
            def file_types_count = file_types.readLines().size()

            RPM_BUILD_DIR.renameTo(package_build_dir)
            RPM_BUILD_DIR.mkdir()

            new File(prep_source_dir, "status.txt").withPrintWriter { status ->
                status.println("LINUX_RPM='$LINUX_RPM'")
                status.println("PACKAGE_INFO='$PACKAGE_INFO'")
                status.println("RPM_ROOT='$RPM_ROOT'")
                status.println("RPM_SPEC_DIR='$RPM_SPEC_DIR'")
                status.println("RPM_SOURCES_DIR='$RPM_SOURCES_DIR'")
                status.println("RPM_BUILD_DIR='$RPM_BUILD_DIR'")
                status.println("name='$package_name'")
                status.println("spec='${spec.name}'")
                status.println("build_dir='${package_build_dir.name}'")
                status.println("command=['${command.join("', '")}']")
                status.println("output_size=${cmd_output.size()}")
                status.println("error_size=${cmd_error.size()}")
                status.println("build_files_count=${build_files_count}")
                status.println("file_types_count=${file_types_count}")
                status.println("exit_code=$exit_code")
            }

            if (exit_code) println "Non-zero exit code: $exit_code"
            if (build_files_count != file_types_count) println "Mismatch btw build_files and file_types!"
        }
    }
}

SortedSet<String> listFilesRecursively(File dir)
{
    def base_path_length = dir.path.length() + 1
    def result = new TreeSet<String>()
    dir.eachFileRecurse { result.add(it.path.substring(base_path_length)) }
    result
}

def find_all_files(File basedir, String filename, File output_file)
{
    def proc = new ProcessBuilder(["find", filename, "-mindepth", "1"]).directory(basedir).start()
    output_file.withWriter { output ->
        proc.waitForProcessOutput(output, new StringWriter())
    }
    proc.exitValue()
}

// file --no-pad --mime --print0 -f ../../packages/xfwm4-theme-nodoka/PrepareSources/sources.txt

def file_mime_types(File basedir, File filenames, File output_file)
{
    def proc = new ProcessBuilder(["file", "-print0", "--no-pad", "--mime", "-f", filenames.absolutePath]).directory(basedir).start()
    output_file.withWriter { output ->
        proc.waitForProcessOutput(output, new StringWriter())
    }
    proc.exitValue()
}
