package org.ifcx.readit.index

import groovy.transform.EqualsAndHashCode
//import edu.stanford.nlp.util.CoreMap
//import edu.stanford.nlp.trees.semgraph.SemanticGraph
//import edu.stanford.nlp.semgrex.SemgrexMatcher

@EqualsAndHashCode(includes="label")
//@ToString(includes="label")
class Slot
{
    String label
    Filler filler
    Boolean multiple
    Closure extractor = { slot, doc_id, sentence_num, sentence, g, m -> [] }

    static public boolean disableSlotFilters = false

    Slot(label, multiple, filler)
    {
        this.label = label
        this.multiple = multiple
        this.filler = filler
    }

    public String toString() { label }

//    Set<Answer> extract(Slot slot, def doc_id, Integer sentence_num, CoreMap sentence, SemanticGraph g, SemgrexMatcher m)
//    {
//        extractor(slot, doc_id, sentence_num, sentence, g, m)
//    }

    // Return true if the given answer should be included for this slot.
    // The value of the answer may be modified from the raw extraction pattern's match.
    boolean filter(Answer answer) { disableSlotFilters || filler.filter(answer) }

    // Return true if the given answer should not be included for this slot when querying for the given name.
    // This occurs for per:alternate_names for example in which first/last names should not be included in the answers.
    boolean redundant(String name, Answer answer) { false }
}
