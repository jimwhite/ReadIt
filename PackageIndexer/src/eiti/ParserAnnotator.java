package eiti;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.LexicalizedParserQuery;
import edu.stanford.nlp.parser.lexparser.ParserAnnotations;
import edu.stanford.nlp.parser.lexparser.ParserConstraint;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.ParserAnnotatorUtils;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

import java.util.List;
import java.util.Properties;

/**
 * This class will add parse information to an Annotation.
 * It assumes that the Annotation already contains the tokenized words
 * as a {@code List<List<CoreLabel>>} under
 * {@code DeprecatedAnnotations.WordsPLAnnotation.class}.
 * If the words have POS tags, they will be used.
 *
 * If the input does not already have sentences, it adds parse information
 * to the Annotation under the key
 * {@code DeprecatedAnnotations.ParsePLAnnotation.class} as a {@code List<Tree>}.
 * Otherwise, they are added to each sentence's coremap (get with
 * {@code CoreAnnotations.SentencesAnnotation}) under
 * {@code CoreAnnotations.TreeAnnotation}).
 *
 * @author Jenny Finkel
 */
public class ParserAnnotator implements Annotator
{
    private final boolean VERBOSE;
    private final LexicalizedParser parser;

    private final Function<Tree, Tree> treeMap;

    /** Do not parse sentences larger than this sentence length */
    int maxSentenceLength;

    /** Time limit in milliseconds to parse each sentence.  Zero means no time limit. */
    long maxSentenceParseTimeMillis = 0;

    public static final String[] DEFAULT_FLAGS = { "-retainTmpSubcategories" };

    public ParserAnnotator() {
        this(true, -1, 0, DEFAULT_FLAGS);
    }

    public ParserAnnotator(boolean verbose, int maxSent) {
        this(verbose, maxSent, 0, DEFAULT_FLAGS);
    }

    public ParserAnnotator(boolean verbose, int maxSent, long maxMillis, String[] flags) {
        this(System.getProperty("parser.model", LexicalizedParser.DEFAULT_PARSER_LOC), verbose, maxSent, maxMillis, flags);
    }

    public ParserAnnotator(String parserLoc,
                           boolean verbose,
                           int maxSent,
                           long maxMillis,
                           String[] flags) {
        this(loadModel(parserLoc, verbose, flags), verbose, maxSent, maxMillis);
    }

    public ParserAnnotator(LexicalizedParser parser, boolean verbose, int maxSent, long maxMillis) {
        this(parser, verbose, maxSent, maxMillis, null);
    }

    public ParserAnnotator(LexicalizedParser parser, boolean verbose, int maxSent, long maxTimeMillis, Function<Tree, Tree> treeMap) {
        VERBOSE = verbose;
        this.parser = parser;
        this.maxSentenceLength = maxSent;
        this.maxSentenceParseTimeMillis = maxTimeMillis;
        this.treeMap = treeMap;
    }


    public ParserAnnotator(String annotatorName, Properties props) {
        String model = props.getProperty(annotatorName + ".model");
        if (model == null) {
            throw new IllegalArgumentException("No model specified for " +
                    "Parser annotator " +
                    annotatorName);
        }
        this.VERBOSE =
                PropertiesUtils.getBool(props, annotatorName + ".debug", true);

        String[] flags =
                convertFlagsToArray(props.getProperty(annotatorName + ".flags"));
        this.parser = loadModel(model, VERBOSE, flags);
        this.maxSentenceLength =
                PropertiesUtils.getInt(props, annotatorName + ".maxlen",
                        Integer.MAX_VALUE);
        this.maxSentenceParseTimeMillis =
                PropertiesUtils.getInt(props, annotatorName + ".maxmillis", 0);
        // TODO: add a parameter for the treeMap?
        this.treeMap = null;
    }

    public static String[] convertFlagsToArray(String parserFlags) {
        if (parserFlags == null) {
            return DEFAULT_FLAGS;
        } else if (parserFlags.trim().equals("")) {
            return StringUtils.EMPTY_STRING_ARRAY;
        } else {
            return parserFlags.trim().split("\\s+");
        }
    }

    private static LexicalizedParser loadModel(String parserLoc,
                                               boolean verbose,
                                               String[] flags) {
        if (verbose) {
            System.err.println("Loading Parser Model [" + parserLoc + "] ...");
        }
        LexicalizedParser result = LexicalizedParser.loadModel(parserLoc, flags);
        // lp.setOptionFlags(new String[]{"-outputFormat", "penn,typedDependenciesCollapsed", "-retainTmpSubcategories"});
        // treePrint = lp.getTreePrint();

        return result;
    }

    public void annotate(Annotation annotation) {
        if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
            // parse a tree for each sentence
            for (final CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
                final List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
                if (VERBOSE) {
                    System.err.println("Parsing: " + words);
                }

                // generate the constituent tree
                Tree tree = null;

                try {
                    // put here the portion of code that may take more than "waitTimeout"
                    if (maxSentenceLength <= 0 || words.size() < maxSentenceLength) {
                        final Tree[] treeMem = { null };

                        Thread thread = new Thread(new Runnable() {
                            public void run()
                            {
                                List<ParserConstraint> constraints = sentence.get(ParserAnnotations.ConstraintAnnotation.class);
                                LexicalizedParserQuery pq = parser.parserQuery();
                                pq.setConstraints(constraints);
                                pq.parse(words);
                                treeMem[0] = pq.getBestParse();
                            }
                        });

                        thread.start();

                        // Wait for the sentence to be parsed for up to our time limit.
                        // If the time limit value is 0 then we'll wait indefinitely (no time limit).
                        thread.join(maxSentenceParseTimeMillis);

                        tree = treeMem[0];

                        // Is the parser is still running?
                        if (thread.isAlive()) {
                            // We've already waited as long as we're willing to.
                            thread.stop();
                        }
                    }
                } catch (InterruptedException e) {
                    if (VERBOSE) {
                        System.err.println("Parsing of sentence timed out!");
                    }
                }

                if (tree == null) {
                    tree = ParserAnnotatorUtils.xTree(words);
                }

                if (treeMap != null) {
                    tree = treeMap.apply(tree);
                }

                ParserAnnotatorUtils.fillInParseAnnotations(VERBOSE, sentence, tree);
            }
        } else {
            throw new RuntimeException("unable to find sentences in: " + annotation);
        }
    }
}
