#!/usr/bin/env groovy
package scripts

// GATE CompoundDocument requires that all of the member documents have the same file encoding.
// So we convert all of the non-utf8 text files to utf8.
// This doesn't really cover all the weird cases.  Some files purposely have bad encodings
// (such as gcc/testsuite/gfortran.dg/bom_error.f90) and sometimes 'file' thinks a non-text
// file is a text file.  Other problems include files whose names use non-ASCII characters
// for which we don't use the right encoding (such as hunspell-wa/BUILD/aspell-wa-0.4.15/mots/nos_d_sacw?).
// This should be dealt with in index_packages.groovy where the metadata can be fixed up to handle those errors.

args = args as List

LINUX_RPM = new File("/mnt/LINUX_RPM")

PACKAGE_INFO = new File(LINUX_RPM, "packages")

index_dir = new File(LINUX_RPM, 'index')

release_id = 'fc17'
packages_dir = PACKAGE_INFO

if (args && args[0] == '--index') {
    args.remove(0)
    index_dir = new File(args.remove(0))
}

if (args && args[0] == '--packages') {
    args.remove(0)
    packages_dir = new File(args.remove(0))
}

if (args && args[0] == '--release_id') {
    args.remove(0)
    release_id = args.remove(0)
}

println "# index_dir      = ${index_dir}"
println "# packages_dir   = ${packages_dir}"
println "# release_id     = ${release_id}"

FILE_SIZE_LIMIT = 1000000

package_count = 0
file_count = 0

tempDir = new File('/tmp')
saveDir = new File('/tmp/saved')

assert escape_path_for_quotes(new File("'foo")) == /\'foo/
assert escape_path_for_quotes(new File("bar '")) == /bar \'/
assert escape_path_for_quotes(new File("\\foo")) == /\\foo/
assert escape_path_for_quotes(new File("bar \\ ")) == /bar \\ /

packages_dir.eachFile { package_dir ->
    if (package_dir.isDirectory()) {
//        println package_dir
        def info = read_package_info_fields(package_dir)

        println "# ${info.name}"

        convert_package(info, package_dir)
    }
}

println "# Converted ${file_count} files in ${package_count} packages."

def void convert_package(Map info, File package_dir)
{
    // name
    // spec
    // release
    // build files dir path

    String build_dir_name = info.build_dir

    def build_files_dir = new File(package_dir, build_dir_name)

    // requires*        trim ' = ...' and unique
    // buildrequires*   "  "
    // provides*        "  "
    // each file:
    //    mime (as guessed by file command)
    //    extension

    def build_files_list = new File(package_dir, "file_types.txt").readLines().collect { it.trim() }.grep()

    build_files_list.each { String file_path_and_type ->
        // Mime type follows the null char.
        def file_mime_type = file_path_and_type.substring(file_path_and_type.indexOf(0) + ": ".length() + 1)
        def file_encoding = ""

        // We don't really need the guessed charset.
        if (file_mime_type.indexOf(';') > 0) {
            file_encoding = file_mime_type.substring(file_mime_type.indexOf("; charset=") + "; charset=".length())
            file_mime_type = file_mime_type.substring(0, file_mime_type.indexOf(';'))
        }

        // If Linux 'file' can't guess the file encoding, chances are it is MS-ANSI aka ISO CP1252 aka Windows-1252.
        // Even if not the right encoding it should give us a valid utf-8 for any 8-bit character so the file is well-formed.
        if (file_encoding.equalsIgnoreCase("unknown-8bit") || file_encoding.equalsIgnoreCase("binary")) {
            file_encoding = "windows-1252"
        }

        if (file_mime_type.startsWith('text/') && !(file_encoding.equalsIgnoreCase("us-ascii") || (file_encoding.equalsIgnoreCase("utf-8")))) {
            def build_file_path = file_path_and_type.substring(0, file_path_and_type.indexOf(0))

            try {
                def build_file = new File(package_dir, build_file_path)

                def file_name = build_file.name

                def file_size = build_file.exists() ? build_file.size() : -1

                if ( build_file_path.startsWith(File.separator))  build_file_path =  build_file_path.substring(File.separator.length())
                // Strip off "BUILD/" subdir prefix. We've added it back on above for the package.build_files_path field.
//                build_file_path = build_file_path.substring(build_dir_name.length() + File.separator.length())

                def converted_file = new File(new File(tempDir, package_dir.path), build_file_path)

                def tempParent = converted_file.parentFile
                if (!tempParent.exists() && !tempParent.mkdirs()) println "# mkdirs failed for $tempParent"

                def saved_file = new File(new File(saveDir, package_dir.path), build_file_path)

                def savedParent = saved_file.parentFile
                if (!savedParent.exists() && !savedParent.mkdirs()) println "# mkdirs failed for $savedParent"

                println "cp '${escape_path_for_quotes(build_file)}' '${escape_path_for_quotes(saved_file)}'"
                println "iconv -f ${file_encoding} -t utf-8 --byte-subst='\\%03o' --unicode-subst='\\u%04x' '${escape_path_for_quotes(build_file)}' >'${escape_path_for_quotes(converted_file)}'"
                println "cp '${escape_path_for_quotes(converted_file)}' '${escape_path_for_quotes(build_file)}'"

                ++file_count
            } catch (Exception e) {
                e.printStackTrace(System.out)
            }
        }
    }

    ++package_count
}

def escape_path_for_quotes(File file)
{
    file.path.replaceAll(/([\\'])/, /\\$1/)
}

def package_name_from_selector(String it)
{
    (it.contains(" ") ? it.substring(0, it.indexOf(' ')) : it).trim()
}

def read_package_info_fields(File package_info_dir)
{
    def result = new File(package_info_dir, "status.txt").readLines().collect { it.trim() }.grep().collectEntries { line ->
        def (_, fname, fvalue) = (line =~ /^(\w+)='?([^']*)'?/)[0]
        [fname, fvalue]
    }
    result
}

