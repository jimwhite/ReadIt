package jaligner.util;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.search.spell.JaroWinklerDistance;
import org.apache.lucene.search.spell.StringDistance;

import java.util.Comparator;

public class JaroWinklerSimilarity implements Comparator<Token>
{
    StringDistance yardstick = new JaroWinklerDistance();

    @Override
    public int compare(Token o1, Token o2)
    {
        double similarity = yardstick.getDistance(o1.toString(), o2.toString());
        int scaled = (int) (20 * similarity) - 6;
        return scaled;
    }
}
