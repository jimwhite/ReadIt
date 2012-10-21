package org.ifcx.extractor

import java.util.regex.Matcher

source_rpm_repo_dir = new File("/run/media/jim/Fedora 17 source")

assert escape_spaces(source_rpm_repo_dir) == "/run/media/jim/Fedora\\ 17\\ source"

source_packages_dir = new File("/mnt/LINUX_RPM/packages")

source_rpm_repo_dir.eachFileRecurse { File srpm ->
    if (srpm.name.endsWith(".src.rpm")) {
        String[] command = ["/bin/sh", "-c", "rpm -i --nodeps ${escape_spaces(srpm)}"]
        println command
        def proc = command.execute()
        println proc.text
        def exit_code = proc.waitFor()
        if (exit_code) println "Non-zero exit code: $exit_code"
    }
}

def escape_spaces(File file)
{
    file.path.replaceAll(" ", Matcher.quoteReplacement("\\ "))
}
