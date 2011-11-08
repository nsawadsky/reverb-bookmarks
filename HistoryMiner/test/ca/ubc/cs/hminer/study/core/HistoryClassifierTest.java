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
                new ArrayList<HistoryVisit>(), WebBrowserType.MOZILLA_FIREFOX);
        
        assertFalse(classifier.isGoogleSearch("https://docs.google.com/#home", "Google Docs - Home"));
        
        assertFalse(classifier.isGoogleSearch("http://www.google.ca/#sclient=psy-ab&hl=en&site=&source=hp&q=testing&pbx=1&oq=testing&aq=f&aqi=g4&aql=1&gs_sm=e&gs_upl=1786l2098l0l2168l7l3l0l0l0l0l75l134l2l2l0&bav=on.2,or.r_gc.r_pw.,cf.osb&fp=606454708746acf6&biw=1104&bih=600",
                "Non-matching title"));

        assertTrue(classifier.isGoogleSearch("http://www.google.ca/#sclient=psy-ab&hl=en&site=&source=hp&q=testing&pbx=1&oq=testing&aq=f&aqi=g4&aql=1&gs_sm=e&gs_upl=1786l2098l0l2168l7l3l0l0l0l0l75l134l2l2l0&bav=on.2,or.r_gc.r_pw.,cf.osb&fp=606454708746acf6&biw=1104&bih=600",
                "testing - Google Search"));

        assertTrue(classifier.isGoogleSearch("https://www.google.ca/#sclient=psy-ab&hl=en&site=&source=hp&q=testing&pbx=1&oq=testing&aq=f&aqi=g4&aql=1&gs_sm=e&gs_upl=1786l2098l0l2168l7l3l0l0l0l0l75l134l2l2l0&bav=on.2,or.r_gc.r_pw.,cf.osb&fp=606454708746acf6&biw=1104&bih=600",
                "testing - Google Search"));

        assertTrue(classifier.isGoogleSearch("http://www.google.ca/search?q=testing&ie=utf-8&oe=utf-8&aq=t&rls=org.mozilla:en-US:official&client=firefox-a",
                "Non-matching title"));
        
        assertTrue(classifier.isGoogleSearch("https://www.google.ca/search?q=testing&ie=utf-8&oe=utf-8&aq=t&rls=org.mozilla:en-US:official&client=firefox-a",
                "Non-matching title"));

        // No text
        Document doc = Jsoup.parse("<html><body></body></html>");
        assertEquals(LocationType.NON_CODE_RELATED, classifier.classifyDocument(doc, false).type);
        
        // Two no-argument method declarations
        doc = Jsoup.parse("<html><body>mymethod() mymethod()</body></html>");
        assertEquals(LocationType.CODE_RELATED, classifier.classifyDocument(doc, false).type);
        
        // Two method declarations, each with an argument
        doc = Jsoup.parse("<html><body>myMethod(int myArg) myMethod(int myArg)</body></html>");
        assertEquals(LocationType.CODE_RELATED, classifier.classifyDocument(doc, false).type);

        // Two C++-style method declarations, each with an argument
        doc = Jsoup.parse("<html><body>my_method(int my_arg) my_method(int my_arg)</body></html>");
        assertEquals(LocationType.CODE_RELATED, classifier.classifyDocument(doc, false).type);

        /*
        // Two method invocations
        doc = Jsoup.parse("<html><body>myInstance.myMethod(5) myInstance.myMethod(5)</body></html>");
        assertEquals(LocationType.CODE_RELATED, classifier.classifyDocument(doc, false));
        
        // Two C++ method invocations
        doc = Jsoup.parse("<html><body>myInstance->myMethod(5); myInstance->myMethod(5);</body></html>");
        assertEquals(LocationType.CODE_RELATED, classifier.classifyDocument(doc, false));
        */

        Pattern pat = Pattern.compile("http://www\\.google\\.\\S+/search");
        
        assertTrue(pat.matcher("http://www.google.co.uk/search?test").find());
        
        assertFalse(pat.matcher("http://www.google.com/calendar").find());

    }

}
