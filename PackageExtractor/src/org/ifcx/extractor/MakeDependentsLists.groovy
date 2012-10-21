package org.ifcx.extractor

LINUX_RPM = new File("/mnt/LINUX_RPM")

PACKAGE_INFO = new File(LINUX_RPM, "packages")

RPM_ROOT = new File(LINUX_RPM, "rpmbuild")
RPM_SPEC_DIR = new File(RPM_ROOT, "SPECS")
RPM_SOURCES_DIR = new File(RPM_ROOT, "SOURCES")
RPM_BUILD_DIR = new File(RPM_ROOT, "BUILD")

RPM_SPEC_DIR.eachFile { File spec ->
    if (spec.name.endsWith(".spec")) {
        def package_name = spec.name - ~/\.spec$/
        def package_info_dir = new File(PACKAGE_INFO, package_name)

        rpmspec(spec, ["--parse"], new File(package_info_dir, 'package.spec'))
        rpmspec(spec, ["--query", "--buildrequires"], new File(package_info_dir, 'buildrequires.txt'))
        rpmspec(spec, ["--query", "--requires"], new File(package_info_dir, 'requires.txt'))
        rpmspec(spec, ["--query", "--provides"], new File(package_info_dir, 'provides.txt'))

    }
}

def Object rpmspec(File spec, List<String> params, File cmd_output)
{
    def command = ["rpmspec", * params, spec.absolutePath]
    def proc = new ProcessBuilder(command).start()

    cmd_output.withWriter { output ->
        new StringWriter().withWriter { error ->
            proc.waitForProcessOutput(output, error)
        }
    }

    def exit_code = proc.exitValue()

    if (exit_code) {
        println "Non-zero exit code: $exit_code from: ${command.join(' ')}"
    }

    exit_code
}
