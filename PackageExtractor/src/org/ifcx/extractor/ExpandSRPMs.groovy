package org.ifcx.extractor

import java.util.regex.Matcher

source_rpm_repo_dir = new File("/run/media/jim/Fedora 17 source")

assert escape_spaces(source_rpm_repo_dir) == "/run/media/jim/Fedora\\ 17\\ source"

source_packages_dir = new File("/mnt/LINUX_RPM/packages")

source_rpm_repo_dir.eachFileRecurse { File srpm ->
    if (srpm.name.endsWith(".src.rpm")) {
        def package_name = srpm.name - ~/\.src\.rpm$/
        def subdir = new File(source_packages_dir, package_name[0].toLowerCase())
        def package_dir = new File(subdir, package_name)
        package_dir.mkdirs()
//        println package_name
//        String command = "/bin/sh -c 'rpm2cpio ${escape_spaces(srpm)} | cpio --extract --preserve-modification-time --make-directories --no-absolute-filenames --no-preserve-owner --unconditional --nonmatching'"
//        String command = "/bin/sh -c 'rpm2cpio ${escape_spaces(srpm)} | cpio -t --nonmatching'"
//        String command = "/bin/sh -c 'pwd'"
        String[] command = ["/bin/sh", "-c", "rpm2cpio ${escape_spaces(srpm)} | cpio --extract --preserve-modification-time --make-directories --no-absolute-filenames --no-preserve-owner --unconditional --nonmatching"]
        println command
        def proc = Runtime.runtime.exec(command, null, package_dir)
        println proc.text
        def exit_code = proc.waitFor()
        if (exit_code) println "Non-zero exit code: $exit_code"
    }
}

def escape_spaces(File file)
{
    file.path.replaceAll(" ", Matcher.quoteReplacement("\\ "))
}
