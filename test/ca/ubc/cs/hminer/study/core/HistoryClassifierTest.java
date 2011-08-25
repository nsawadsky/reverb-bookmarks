package ca.ubc.cs.hminer.study.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import ca.ubc.cs.hminer.study.core.HistoryClassifier;
import ca.ubc.cs.hminer.study.core.HistoryVisit;
import ca.ubc.cs.hminer.study.core.LocationType;

public class HistoryClassifierTest {

    @Test
    public void testClassifyVisit() {
        HistoryClassifier classifier = new HistoryClassifier(
                new ArrayList<HistoryVisit>());
        
        // No text
        Document doc = Jsoup.parse("<html><body></body></html>");
        assertEquals(LocationType.NON_CODE_RELATED, classifier.classifyDocument(doc, false));
        
        // Two no-argument method declarations
        doc = Jsoup.parse("<html><body>mymethod() mymethod()</body></html>");
        assertEquals(LocationType.CODE_RELATED, classifier.classifyDocument(doc, false));
        
        // Two method declarations, each with an argument
        doc = Jsoup.parse("<html><body>myMethod(int myArg) myMethod(int myArg)</body></html>");
        assertEquals(LocationType.CODE_RELATED, classifier.classifyDocument(doc, false));

        // Two method invocations
        doc = Jsoup.parse("<html><body>myInstance.myMethod(5); myInstance.myMethod(5);</body></html>");
        assertEquals(LocationType.CODE_RELATED, classifier.classifyDocument(doc, false));
        
        Pattern pat = Pattern.compile("http://www\\.google\\.\\S*/search");
        
        assertTrue(pat.matcher("http://www.google.co.uk/search?test").find());
        
        assertFalse(pat.matcher("http://www.google.com/calendar").find());

    }

}
