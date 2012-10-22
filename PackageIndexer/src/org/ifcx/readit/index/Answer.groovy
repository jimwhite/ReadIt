package org.ifcx.readit.index

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
//import edu.stanford.nlp.ling.IndexedWord
//import edu.stanford.nlp.util.CoreMap

// An answer is unique based on the slot and the value.
@EqualsAndHashCode(includes="slot,value")
// For development it can be helpful to have each match be unique.
// @EqualsAndHashCode(includes="slot,pattern,doc_id,sent_num,value")
@ToString
class Answer
{
    Slot slot
    String pattern
    String doc_id
    Integer sent_num

//    CoreMap sentence
//    List<IndexedWord> words

    String stringValue

    String getValue()
    {
        words ? words.collect { it.word() }.join(' ') : stringValue
    }

//    public String toString() { }
}
