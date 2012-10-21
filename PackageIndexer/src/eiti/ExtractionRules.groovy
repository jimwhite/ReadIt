package eiti

import edu.stanford.nlp.util.CoreMap
import edu.stanford.nlp.semgrex.SemgrexMatcher
import edu.stanford.nlp.trees.semgraph.SemanticGraph
import edu.stanford.nlp.ling.CoreAnnotations

import static Model.*
import edu.stanford.nlp.ling.IndexedWord
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations

class ExtractionRules
{
    Map<Slot, List<String>> rules = [:].withDefault { [] } ;

    ExtractionRules()
    {
        def simple_extractor = { Slot slot, def doc_id, Integer sent_num, CoreMap sentence, SemanticGraph g, SemgrexMatcher m ->
            Set<Answer> answers = []

            while (m.find()) {
                def entity = m.getNode('entity')
                def value = m.getNode('value')

                if (entity && value) {
                    def answer = new Answer(slot: slot, doc_id: doc_id, sent_num: sent_num, sentence: sentence, words: child_text_words(value, sentence))

                    if (slot.filter(answer)) answers.add(answer)
                }
            }

            answers
        }

        extractor(PER_AGE,simple_extractor)
        extractor(PER_CAUSE_OF_DEATH,simple_extractor)
        extractor(PER_CHARGES,simple_extractor)
        extractor(PER_CHILDREN,simple_extractor)
        extractor(PER_DATE_OF_BIRTH,simple_extractor)
        extractor(PER_DATE_OF_DEATH,simple_extractor)
        extractor(PER_EMPLOYEE_OF,simple_extractor)
        extractor(PER_MEMBER_OF,simple_extractor)
        extractor(PER_ORIGIN,simple_extractor)
        extractor(PER_OTHER_FAMILY,simple_extractor)
        extractor(PER_PARENTS,simple_extractor)
        extractor(PER_COUNTRY_OF_BIRTH,simple_extractor)
        extractor(PER_STATEORPROVINCE_OF_BIRTH,simple_extractor)
        extractor(PER_CITY_OF_BIRTH,simple_extractor)
        extractor(PER_COUNTRY_OF_DEATH,simple_extractor)
        extractor(PER_STATEORPROVINCE_OF_DEATH,simple_extractor)
        extractor(PER_CITY_OF_DEATH,simple_extractor)
        extractor(PER_RELIGION,simple_extractor)
        extractor(PER_COUNTRIES_OF_RESIDENCE,simple_extractor)
        extractor(PER_STATEORPROVINCES_OF_RESIDENCE,simple_extractor)
        extractor(PER_CITIES_OF_RESIDENCE,simple_extractor)
        extractor(PER_SCHOOLS_ATTENDED,simple_extractor)
        extractor(PER_SIBLINGS,simple_extractor)
        extractor(PER_SPOUSE,simple_extractor)
        extractor(PER_TITLE,simple_extractor)

        extractor(PER_ALTERNATE_NAMES, { Slot slot, def doc_id, Integer sent_num, CoreMap sentence, SemanticGraph g, SemgrexMatcher m ->
            Set<Answer> answers = []

            while (m.find()) {
                def entity = m.getNode('entity1') ?: m.getNode('entity2')
                def name = m.getNode('value1') ?: m.getNode('value2')

                if (entity && name) {
                    def answer = new Answer(slot: slot, doc_id: doc_id, sent_num: sent_num, sentence: sentence, words: entity_name_words(name, sentence))

                    if (slot.filter(answer)) answers.add(answer)
                }
            }

            answers
        })

    }

    ExtractionRules(File pattern_file)
    {
        pattern_file.eachLine {
            def fields = it.split('\t')
            rule(fields[0], fields[1])
        }
    }

    def rule(Slot slot, String pattern)
    {
        rules[slot] = rules[slot] + [pattern]
    }

    def rule(String label, String pattern)
    {
        def slot = Model.slots[label]
        rules[slot] = rules[slot] + [pattern]
    }

    def extractor(Slot slot, Closure closure)
    {
        slot.extractor = closure
    }

    def extractor(String label, Closure closure)
    {
        Model.slots[label].extractor = closure
    }

    List<IndexedWord> entity_name_words(IndexedWord head, CoreMap sentence)
    {
        SemanticGraph g = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation)
        g.descendants(head).sort()
    }

    List<IndexedWord> child_text_words(IndexedWord head, CoreMap sentence)
    {
        SemanticGraph g = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation)
        g.descendants(head).sort()
    }
}
