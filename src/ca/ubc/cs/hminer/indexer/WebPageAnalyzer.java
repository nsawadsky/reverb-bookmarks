package ca.ubc.cs.hminer.indexer;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

/**
 * Filters {@link StandardTokenizer} with {@link StandardFilter}, {@link
 * LowerCaseFilter} and {@link StopFilter}, using a list of
 * English stop words.
 *
 * <a name="version"/>
 * <p>You must specify the required {@link Version}
 * compatibility when creating StandardAnalyzer:
 * <ul>
 *   <li> As of 3.1, StandardTokenizer implements Unicode text segmentation,
 *        and StopFilter correctly handles Unicode 4.0 supplementary characters
 *        in stopwords.  {@link ClassicTokenizer} and {@link ClassicAnalyzer} 
 *        are the pre-3.1 implementations of StandardTokenizer and
 *        StandardAnalyzer.
 *   <li> As of 2.9, StopFilter preserves position increments
 *   <li> As of 2.4, Tokens incorrectly identified as acronyms
 *        are corrected (see <a href="https://issues.apache.org/jira/browse/LUCENE-1068">LUCENE-1068</a>)
 * </ul>
 */
public final class WebPageAnalyzer extends StopwordAnalyzerBase {

    /** Default maximum allowed token length */
    private static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

    private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

    /** An unmodifiable set containing some common English words that are usually not
        useful for searching. */
    private static final Set<String> stopWordsSet;

    static {
        stopWordsSet = new HashSet<String>();
        for (Object word: StopAnalyzer.ENGLISH_STOP_WORDS_SET) {
            String wordString = (String)word;
            stopWordsSet.add(wordString);
            
            StringBuffer capWord = new StringBuffer(Character.toUpperCase(wordString.charAt(0)));
            capWord.append(wordString.substring(1));
            stopWordsSet.add(capWord.toString());
        }
    }
    
    public WebPageAnalyzer() {
        super(Version.LUCENE_33, stopWordsSet);
    }

    /**
     * Set maximum allowed token length.  If a token is seen
     * that exceeds this length then it is discarded.  This
     * setting only takes effect the next time tokenStream or
     * reusableTokenStream is called.
     */
    public void setMaxTokenLength(int length) {
        maxTokenLength = length;
    }

    /**
     * @see #setMaxTokenLength
     */
    public int getMaxTokenLength() {
        return maxTokenLength;
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
        final StandardTokenizer src = new StandardTokenizer(matchVersion, reader);
        src.setMaxTokenLength(maxTokenLength);
        TokenStream tok = new StandardFilter(matchVersion, src);
        //tok = new LowerCaseFilter(matchVersion, tok);
        tok = new StopFilter(matchVersion, tok, stopwords);
        return new TokenStreamComponents(src, tok) {
            @Override
            protected boolean reset(final Reader reader) throws IOException {
                src.setMaxTokenLength(WebPageAnalyzer.this.maxTokenLength);
                return super.reset(reader);
            }
        };
    }
}
