package org.ifcx.readit.index

import java.util.regex.Pattern

public class RPM_SPEC
{
    public static enum Section
    {
        BUILD('build'), CHANGELOG('changelog'), CHECK('check'), CLEAN('clean'),
        DESCRIPTION('description'), FILES('files'), INSTALL('install'),
        PACKAGE('package'), PREP('prep'), PRE('pre'), POST('post'), PRE_UN('preun'), POST_UN('postun'),
        TRIGGER('trigger'), TRIGGER_IN('triggerin'), TRIGGER_UN('triggerun'), TRIGGER_PRE_IN('triggerprein'),
        TRIGGER_POST_UN('triggerpostun'), PRE_TRANS('pretrans'), POST_TRANS('posttrans');

        final String token

        Section(String token)
        {
            this.token = token
        }
    }

    static Map<String, Section> token_to_section = Section.enumConstants.collectEntries { [it.token, it] }

    // Match section tokens in reverse order of length so prefixes don't make a partial match.
    // Can't rely on a trailing space since there may be none.
    static section_pattern = ~/^%(${token_to_section.keySet().sort { -it.length() }.join('|')})\s*+(?:-f\s+\S+\s*)?([^-\s]*+)\s*+(.*)$/
//    static section_pattern = ~/^%(${token_to_section.keySet().sort { -it.length() }.join('|')})\s*+((?:-f\s+\S+\s*+)?[^-\s]*+)\s*+(.*)$/

    def current_section = Section.PACKAGE
    def current_package = ""
    def section_text = ""
    Map<String, Map<Section, String>> package_to_section_values = [:]

    public static Map<String, Map<Section, String>> parse(String spec_text)
    {
        def parser = new RPM_SPEC()
        parser.do_parse(spec_text)
        parser.package_to_section_values
    }

    public void do_parse(String spec_text)
    {
        // Could redo this using multiline regexes rather than line-at-a-time,
        // but this is already done and is good enough for now.
        spec_text.eachLine { line ->
            def matcher = section_pattern.matcher(line)
            if (matcher.matches()) {
                section_close()
                current_section = token_to_section[matcher.group(1)]
                current_package = matcher.group(2)
                section_text = matcher.group(3) ? "#scriplet param: " + matcher.group(3) : ""
            } else {
                section_append(line)
            }
        }
        section_close()
    }

    void section_close()
    {
        if (!package_to_section_values.containsKey(current_package)) {
            package_to_section_values[current_package] = [:]
        }

        if (current_section == Section.CHANGELOG) {
            def lines = section_text.readLines()
            if (lines.size() > 10) lines = lines[0..9]  + ["..."]
            section_text = lines.join('\n')
        }

        package_to_section_values[current_package][current_section] = section_text
    }

    void section_append(String s)
    {
        section_text = section_text ? section_text + '\n' + s : s
    }

    static Pattern key_value_pattern = ~/s*([^:\s]+)\s*:(.*)$/
    static List parse_package_section(String package_section) {
        List values = []
//        package_section.eachMatch(~/\s*([^:\s])+\s*:(.*)$/) { key, value -> values.add([key:key, value:value.trim()]) }
        package_section.eachLine {
            it = it.trim()
            if (it && !it.startsWith('#')) {
                def matcher = key_value_pattern.matcher(it)
                if (matcher.matches()) {
                    def key = matcher.group(1)
                    def value = matcher.group(2)
                    values.add([key:key.toLowerCase(), value:value.trim()])
                }
            }
        }
        values
    }
}
