package org.ifcx.readit.index

import edu.stanford.nlp.pipeline.Annotation
import edu.stanford.nlp.io.IOUtils
import edu.stanford.nlp.util.CoreMap
import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.dcoref.CorefChain
import edu.stanford.nlp.dcoref.CorefCoreAnnotations
import edu.stanford.nlp.trees.semgraph.SemanticGraph
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations
import edu.stanford.nlp.ling.IndexedWord
import edu.stanford.nlp.semgrex.SemgrexPattern
import java.util.regex.Pattern

class Retriever
{
    File preprocessed_dir
    ExtractionRules extraction_rules = new ExtractionRulesWithPatterns()
    List<String> all_slot_labels
    List<Slot> _slots
    String query_name
    File documents_file
    Inference inference = new Inference()

    Map<Slot, Set<Answer>> all_answers = [:].withDefault { [] as Set<Answer> }

    static dependenciesAnnotationClazz = SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class

    def NOISY = 0

    Retriever()
    {
    }

    Retriever(File query_folder, File preprocessed_dir)
    {
        this.preprocessed_dir = preprocessed_dir
        documents_file = new File(query_folder, 'documents.txt')
        query_name = new File(query_folder, 'name.txt').text.trim()
        slots = new File(query_folder, 'slots.txt').readLines()
    }

    List<Slot> getSlots()
    {
        _slots
    }

    void setSlots(List<String> slot_names)
    {
        all_slot_labels = slot_names
        _slots = slot_names.collect { Model.slots[it] }.grep { it }
    }

    def do_all_documents()
    {
        documents_file.eachLine {  do_document(new File(it).name - ~/\.sgm$/) }

        all_answers
    }

    Map<Slot, Set<Answer>> do_document(def doc_id)
    {
        def preprocessed_file = new File(preprocessed_dir, doc_id + ".sgm.ser.gz")

        if (!(preprocessed_file.exists() ))  {
            System.err.println("file ${preprocessed_file} doesn't exist")
            return null
        }

        Annotation annotation = null

        try {
            annotation = IOUtils.readObjectFromFile(preprocessed_file)
        } catch (Error e) {
            // We catch Error here even though it is usually a bad idea because of some (a)
            // preprocessed file that causes us to get an OutOfMemory error when reading it.
            // Problem files:
            //      output/preprocessed/eng-WL-11-174598-12964778.sgm.ser.gz
            //      output/preprocessed/eng-WL-11-174598-12964978.sgm.ser.gz
            e.printStackTrace()
            System.err.println "Error reading object file: ${preprocessed_file}"
            return null
        } catch (Exception e) {
            // java.io.UTFDataFormatException from output/preprocessed/APW_ENG_20080505.0655.LDC2009T13.sgm.ser.gz
            e.printStackTrace()
            System.err.println "Exception reading object file: ${preprocessed_file}"
            return null
        }

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class)

        Map<Integer, CorefChain> coref_chains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class)

        coref_chains.each { coref_num, CorefChain coref_chain ->
            if (NOISY > 1) {
                coref_chain.corefMentions.each { log.info it }
            }

            if (coref_chain_mentions_name(coref_chain, query_name)) {
                if (NOISY == 1) {
                    coref_chain.corefMentions.each { log.info it }
                }

                coref_chain.corefMentions.each { mention ->
                    def sentence = sentences[mention.sentNum - 1]
                    SemanticGraph g = sentence.get(dependenciesAnnotationClazz)

                    // Don't change shared annotation.
                    g = new SemanticGraph(g)

                    Set<IndexedWord> vertexSet = g.vertexSet()

                    // Mark the nodes for all tokens (that have one) except the head token as being {features:mentionToken}.
                    ((mention.startIndex..<mention.endIndex)-[mention.headIndex]).each { x ->
                        // If we're using collapsed dependencies (as we are), not all tokens will have a node in the graph.
                        (vertexSet.find { it.index() == x })?.set(CoreAnnotations.FeaturesAnnotation, 'mentionToken')
                    }

                    // Mark the node for the head token as {features:mention}.
                    vertexSet.find { it.index() == mention.headIndex }?.set(CoreAnnotations.FeaturesAnnotation, 'mention')

                    // Need to make a copy to work around an inconsistency internal to SemanticGraph when applying new annotations.
                    g = new SemanticGraph(g)

                    if (g.getRoots().size()) {
                        def answer_map = do_sentence(doc_id, mention.sentNum, sentence, g)

                        answer_map.each {
                            slot, Set<Answer> answers -> all_answers[slot] = all_answers[slot] + answers
                        }
                    }
                }
            }
        }

        all_answers
    }

    Set<Integer> sentence_numbers_with_mentions(CorefChain coref_chain, def sentences)
    {
        def sentences_nums = [] as Set<Integer>

        coref_chain.corefMentions.each { mention ->
            def sentence = sentences[mention.sentNum - 1]
            SemanticGraph g = sentence.get(dependenciesAnnotationClazz)
            Set<IndexedWord> vertexSet = g.vertexSet()

            // Mark the nodes for all tokens (that have one) except the head token as being {features:mentionToken}.
            ((mention.startIndex..<mention.endIndex)-[mention.headIndex]).each { x ->
                // If we're using collapsed dependencies (as we are), not all tokens will have a node in the graph.
                (vertexSet.find { it.index() == x })?.set(CoreAnnotations.FeaturesAnnotation, 'mentionToken')
            }

            // Mark the node for the head token as {features:mention}.
            vertexSet.find { it.index() == mention.headIndex }?.set(CoreAnnotations.FeaturesAnnotation, 'mention')

            sentences_nums << mention.sentNum
        }

        sentences_nums
    }

    static def coref_chain_mentions_name(CorefChain coref_chain, String name)
    {
        def pattern = ~(/(?i)[^\p{L}]*(?:-LRB-.*-RRB-[^\p{L}]*)?/ + Pattern.quote(name) + /(?:\s*'s)?(?:[^\p{L}]*-LRB-.*-RRB-)?[^\p{L}]*/)
        // http://localhost:5000/query/SF101/document/eng-WL-11-174592-12943032
        // http://localhost:5000/query/SF124/document/eng-WL-11-174597-12966129
//        def mentions = coref_chain.corefMentions.collect { it.mentionSpan.toLowerCase() }
//        mentions.find { it.indexOf(name) >= 0 } ? true : false
        coref_chain.corefMentions.find { pattern.matcher(it.mentionSpan).matches() } ? true : false
    }

    Map<Slot, Set<Answer>> do_sentence(doc_id, sentence_num, CoreMap sentence, SemanticGraph g)
    {
        Map<Slot, Set<Answer>> slot_to_answers = slots.collectEntries { slot ->
            Set<Answer> answers = [] as Set<Answer>

            extraction_rules.rules[slot].each { pattern ->
                def some = slot.extract(slot, doc_id, sentence_num, sentence, g, SemgrexPattern.compile(pattern).matcher(g))
                some.each { Answer answer ->
                    answer.pattern = pattern

                    if (!slot.redundant(query_name, answer)) { answers.add(answer) }
                }
            }

            [slot, answers]
        }

        slot_to_answers.keySet().each { Slot slot ->
            Set<Answer> inferred_answers = [] as Set<Answer>
            switch (slot) {
                case Model.PER_CITIES_OF_RESIDENCE :
                    slot_to_answers[slot].each { Answer answer ->
                        inferred_answers.addAll(inference.expand_city(answer, Model.PER_STATEORPROVINCES_OF_RESIDENCE, Model.PER_COUNTRIES_OF_RESIDENCE))
                    }
                    break
// These should be inferred as well, but they weren't included in the system at D4 deadline so can't use 'em yet.
//                case Model.PER_CITY_OF_BIRTH :
//                    slot_to_answers[slot].each { Answer answer ->
//                        inferred_answers.addAll(inference.expand_city(answer, Model.PER_STATEORPROVINCE_OF_BIRTH, Model.PER_COUNTRY_OF_BIRTH))
//                    }
//                    break
//                case Model.PER_CITY_OF_DEATH :
//                    slot_to_answers[slot].each { Answer answer ->
//                        inferred_answers.addAll(inference.expand_city(answer, Model.PER_STATEORPROVINCE_OF_DEATH, Model.PER_COUNTRY_OF_DEATH))
//                    }
//                    break
            }
            inferred_answers.each { Answer answer ->
                if (slot_to_answers.containsKey(answer.slot)) slot_to_answers[answer.slot].add(answer)
            }
        }

        slot_to_answers
    }

}
