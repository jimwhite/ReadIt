package jaligner.test

import jaligner.SmithWatermanGotoh
import jaligner.formats.Pair

import jaligner.Sequence
import jaligner.util.JaroWinklerSimilarity
import jaligner.util.LevenshteinDistance
import org.apache.lucene.analysis.Token
import org.apache.lucene.analysis.WhitespaceAnalyzer
import org.apache.lucene.analysis.standard.ClassicAnalyzer
import org.apache.lucene.util.Version

def analyzer = new WhitespaceAnalyzer()

//def a = new Sequence(" How now brown cow? The quick brown fox jumped over the lazy dog! ")
//def b = new Sequence(" The brown cox quickly jumped over the dog ")

//def ta = new File('/mnt/LINUX_RPM/packages/qjson/BUILD/qjson/README').text
//def a = new Sequence(ta, analyzer)
//
//def b = new Sequence("""JSON is a lightweight data-interchange format. It can represents integer, real
//number, string, an ordered sequence of value, and a collection of
//name/value pairs.QJson is a qt-based library that maps JSON data to
//QVariant objects.
//""", analyzer)

//def ta = new File('/mnt/LINUX_RPM/packages/ed/BUILD/ed-1.5/README').text
//def a = new Sequence(ta, analyzer)
//def b = new Sequence("""Ed is a line-oriented text editor, used to create, display, and modify
//text files (both interactively and via shell scripts).  For most
//purposes, ed has been replaced in normal usage by full-screen editors
//(emacs and vi, for example).
//
//Ed was the original UNIX editor, and may be used by some programs.  In
//general, however, you probably don't need to install it and you probably
//won't use it.
//
//""", analyzer)

def ta = new File('/mnt/LINUX_RPM/packages/eekboard/BUILD/eekboard-1.0.7/README').text
def a = new Sequence(ta, analyzer)

def b = new Sequence("""eekboard is a virtual keyboard software package, including a set of
tools to implement desktop virtual keyboards.
""", analyzer)

println b


def alignment = SmithWatermanGotoh.align(a, b, new JaroWinklerSimilarity(), 5, 4)

println alignment.summary
println new Pair().format(alignment)

//alignment = SmithWatermanGotoh.align(b, a, null, -5, -3)
//
//println alignment.summary
//println new Pair().format(alignment)
//
