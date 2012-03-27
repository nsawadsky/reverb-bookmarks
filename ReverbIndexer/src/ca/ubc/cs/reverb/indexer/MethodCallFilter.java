package ca.ubc.cs.reverb.indexer;

/**
 * Based on LowerCaseFilter from Lucene 3.3 source code.
 * 
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

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

/**
 * Ensures that tokens containing dots (such as method calls and package names) result in 
 * tokens for each of the dot-separated elements, as well as for the entire token.
 * 
 * <a name="version"/>
 * <p>You must specify the required {@link Version}
 * compatibility when creating MethodCallFilter.
 */
public final class MethodCallFilter extends TokenFilter {
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private boolean inAttribute = false;
  private int inAttributeIndex = 0;
  private char[] inAttributeBuffer = null;
  
  /**
   * Create a new MethodCallFilter.
   * 
   * @param matchVersion See <a href="#version">above</a>
   * @param in TokenStream to filter
   */
  public MethodCallFilter(Version matchVersion, TokenStream in) {
    super(in);
  }
  
  @Override
  public final boolean incrementToken() throws IOException {
      if (!inAttribute) {
          if (!input.incrementToken()) {
              return false;
          }
          final char[] buffer = termAtt.buffer();
          final int length = termAtt.length();
          for (int i = 1; i < length-1; i++) {
              if (buffer[i] == '.') {
                  // If buffer contains a '.' which is not in first or last position
                  inAttribute = true;
                  inAttributeIndex = 0;
                  inAttributeBuffer = Arrays.copyOf(buffer, length);
                  break;
              }
          }
          if (!inAttribute) {
              return true;
          }
      }
      // inAttribute must be true if we reach here.
      
      // Skip start point ahead to first non '.' character.
      while (inAttributeIndex < inAttributeBuffer.length && inAttributeBuffer[inAttributeIndex] == '.') {
          inAttributeIndex++;
      }
      
      // Skip end point ahead to end of buffer or first '.' character.
      int endIndex = inAttributeIndex;
      while (endIndex < inAttributeBuffer.length && inAttributeBuffer[endIndex] != '.') {
          endIndex++;
      }
      
      if (endIndex > inAttributeIndex) {
          // We got some non '.' characters.
          termAtt.copyBuffer(inAttributeBuffer, inAttributeIndex, endIndex - inAttributeIndex);
          inAttributeIndex = endIndex;
      } else {
          // We are finished with this token.  Make sure we also return the entire token as a separate token.
          termAtt.copyBuffer(inAttributeBuffer, 0, inAttributeBuffer.length);
          inAttribute = false;
          inAttributeBuffer = null;
      }
      return true;
  }
}
