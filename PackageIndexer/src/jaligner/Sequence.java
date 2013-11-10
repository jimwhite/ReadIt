/*
 * $Id: Sequence.java,v 1.6 2006/07/27 16:28:24 ahmed Exp $
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package jaligner;

import jaligner.util.Commons;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.tokenattributes.*;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.Vector;

/**
 * A basic (nucleic or protein) sequence. It's a wrapper to
 * {@link java.lang.String}.
 * 
 * @author Ahmed Moustafa (ahmed@users.sf.net)
 */

public class Sequence implements Serializable {
	/**
	 * Sequence
	 */
	private String sequence;

	/**
	 * Sequence id.
	 */
	private String id = null;

	/**
	 * Sequence description.
	 */
	private String description = null;

    private Token[] tokens = null;

	/**
	 * Constructor
	 */
	public Sequence() {
		super();
	}

    /**
     * Constructor
     *
     * @param sequence
     */
    public Sequence(String sequence, Analyzer analyzer) throws IOException {
        this(sequence, analyzer, Integer.MAX_VALUE);
    }

	/**
	 * Constructor
	 * 
	 * @param sequence
	 */
	public Sequence(String sequence, Analyzer analyzer, int max_length) throws IOException {
		super();
		this.sequence = sequence;

        TokenStream stream  = analyzer.tokenStream("contents", new StringReader(sequence));
        Token.TokenAttributeFactory tokenAttributeFactory = new Token.TokenAttributeFactory(stream.getAttributeFactory());

        Vector<Token> tokenVector = new Vector<Token>();

        while (stream.incrementToken() && tokenVector.size() < max_length) {
//            Token token = new Token();
//            Token token = (Token) stream.getAttribute(CharTermAttribute.class);
            Token token = (Token) tokenAttributeFactory.createAttributeInstance(Token.class);

            CharTermAttribute charTerm = stream.getAttribute(CharTermAttribute.class);
            OffsetAttribute offset = stream.getAttribute(OffsetAttribute.class);
//            PayloadAttribute payload = stream.getAttribute(PayloadAttribute.class);
//            FlagsAttribute flags = stream.getAttribute(FlagsAttribute.class);

//        public Token reinit(char[] newTermBuffer, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset, String newType) {
            token.reinit(charTerm.buffer(), 0, charTerm.length(), offset.startOffset(), offset.endOffset());
            token.setOffset(offset.startOffset(), offset.endOffset());

//            token.setPayload(payload.getPayload());
//            token.setFlags(flags.getFlags());

            if (stream.hasAttribute(PositionIncrementAttribute.class)) {
                PositionIncrementAttribute positionIncrement = stream.getAttribute(PositionIncrementAttribute.class);
                token.setPositionIncrement(positionIncrement.getPositionIncrement());
            }

            if (stream.hasAttribute(TypeAttribute.class)) {
                TypeAttribute type = stream.getAttribute(TypeAttribute.class);
                token.setType(type.type());
            }

            tokenVector.add(token);
        }

        stream.end();
        stream.close();

        this.tokens = tokenVector.toArray(new Token[tokenVector.size()]);
    }
	
	/**
	 * Returns the sequence string
	 * 
	 * @return Returns the sequence
	 */
	public String getSequence() {
		return sequence;
	}

	/**
	 * Sets the sequence string
	 * 
	 * @param sequence
	 *            The sequence to set
	 */
	public void setSequence(String sequence) {
		this.sequence = sequence;
        tokens = null;
	}

	/**
	 * Returns the sequence id
	 * 
	 * @return Returns the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * Sets the sequence id
	 * 
	 * @param id
	 *            The id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Returns the sequence description
	 * 
	 * @return Returns the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the sequence description
	 * 
	 * @param description
	 *            The description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Returns the length of the sequence
	 * 
	 * @return sequence length
	 */
	public int length() {
        return getTokens().length;
	}

//	/**
//	 * Returns a subsequence
//	 *
//	 * @param index
//	 *            start index
//	 * @param length
//	 *            length of subsequence
//	 * @return subsequence
//	 */
//	public String subsequence(int index, int length) {
//		return this.sequence.substring(index, index + length);
//	}
//
//	/**
//	 * Returns the acid at specific location in the sequence
//	 *
//	 * @param index
//	 * @return acid at index
//	 */
//	public char acidAt(int index) {
//		return this.sequence.charAt(index);
//	}

	/**
	 * Returns the sequence as an array of tokens.
	 * 
	 * @return array of chars.
	 */
	public Token[] getTokens() {
        return tokens;
	}

    public void setTokens(Token[] t) {
        tokens = t;
    }
    
    /**
     * Returns the sequence id and the sequence string
     * 
     * @return Returns the sequence id and the sequence string
     */
    public String toString() {
        return id + Commons.TAB + sequence;
    }
}