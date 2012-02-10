package ca.ubc.cs.reverb.indexer;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.IndexSearcher;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;
import org.junit.Before;
import org.junit.Test;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;


public class WebPageSearcherTest {
    private JsonNode testData;
    
    @Before
    public void setup() throws JsonParseException, IOException {
        InputStream testDataStream = WebPageSearcherTest.class.getResourceAsStream("WebPageSearcherTestData.json");
        
        try {
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createJsonParser(testDataStream);
            parser.setCodec(new ObjectMapper());
            testData = parser.readValueAsTree();
        } finally {
            if (testDataStream != null) {
                testDataStream.close();
            }
        }
    }
    
    @Test
    public void testFilterGroupAndReorder() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("QueryA", "QueryA"));
        inputQueries.add(new IndexerQuery("QueryA", "QueryA"));
        inputQueries.add(new IndexerQuery("QueryB", "QueryB"));
        
        WebPageSearcher searcher = new WebPageSearcher() {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString == "QueryA") {
                    result.add(new Hit("UrlA_A", "UrlA_A", 2.0F, 1.0F));
                    result.add(new Hit("UrlB_B", "UrlB_B", 1.0F, 1.0F));
                } else if (queryString == "QueryB") {
                    result.add(new Hit("UrlB_A", "UrlB_A", 1.0F, 1.0F));
                    result.add(new Hit("UrlB_B", "UrlB_B", 2.0F, 1.0F));
                } 
                return result;
            }

        };
        
        BatchQueryReply result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testFilterGroupAndReorder");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testFrecencyBoost() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("QueryA", "QueryA"));
        inputQueries.add(new IndexerQuery("QueryB", "QueryB"));
        
        WebPageSearcher searcher = new WebPageSearcher() {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString == "QueryA") {
                    Hit hit = new Hit("UrlA_A", "UrlA_A", 3.0F, 1.0F);
                    result.add(hit);
                } else if (queryString == "QueryB") {
                    Hit hit = new Hit("UrlB_A", "UrlB_A", 2.0F, 2.0F);
                    result.add(hit);
                } 
                return result;
            }

        };
        
        BatchQueryReply result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testFrecencyBoost");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testMerge() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("QueryA", "QueryA"));
        inputQueries.add(new IndexerQuery("QueryB", "QueryB"));
        inputQueries.add(new IndexerQuery("QueryC", "QueryC"));
        
        WebPageSearcher searcher = new WebPageSearcher() {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString == "QueryA") {
                    result.add(new Hit("UrlA_A", "UrlA_A", 4.0F, 1.0F));
                    result.add(new Hit("UrlB_A", "UrlB_A", 1.1F, 1.0F));
                    result.add(new Hit("UrlB_B", "UrlB_B", 1.0F, 1.0F));
                } else if (queryString == "QueryB") {
                    result.add(new Hit("UrlB_A", "UrlB_A", 2.0F, 1.0F));
                    result.add(new Hit("UrlB_B", "UrlB_B", 2.0F, 1.0F));
                } else if (queryString == "QueryC") {
                    result.add(new Hit("UrlC_A", "UrlC_A", 1.1F, 1.0F));
                    result.add(new Hit("UrlC_B", "UrlC_B", 1.0F, 1.0F));
                } 
                return result;
            }

        };
        
        BatchQueryReply result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testMerge");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }
    
    @Test
    public void testCompactHitInfos() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("QueryA", "QueryA"));
        
        WebPageSearcher searcher = new WebPageSearcher() {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString == "QueryA") {
                    result.add(new Hit("http://www.test.com/docs/1.2.3/TestFirstDifferent/1.2/rest", "TestFirstDifferent", 1.0F, 2.0F));
                    result.add(new Hit("http://www.test.com/docs/1.2.4/TestFirstDifferent/1.2/rest", "TestFirstDifferent", 1.0F, 2.0F));
                    result.add(new Hit("http://www.test.com/docs/1.2.5/TestFirstDifferent/1.2/rest", "TestFirstDifferent", 1.0F, 2.0F));
                    result.add(new Hit("http://www.test.com/docs/1.2.4/TestFirstDifferentReverse/1.2/rest", "TestFirstDifferentReverse", 1.0F, 2.5F));
                    result.add(new Hit("http://www.test.com/docs/1.2.3/TestFirstDifferentReverse/1.2/rest", "TestFirstDifferentReverse", 1.0F, 2.0F));
                    result.add(new Hit("http://www.test.com/docs/1.2.3/TestSecondDifferent/1.2/rest", "TestSecondDifferent", 1.0F, 2.0F));
                    result.add(new Hit("http://www.test.com/docs/1.2.3/TestSecondDifferent/2.2/rest", "TestSecondDifferent", 1.0F, 2.0F));
                    result.add(new Hit("http://www.test.com/docs/1.2.3/TestEndsWithVersion/1.2", "TestEndsWithVersion", 1.0F, 2.0F));
                    result.add(new Hit("http://www.test.com/docs/1.2.3/TestEndsWithVersion/2.2", "TestEndsWithVersion", 1.0F, 1.5F));
                    result.add(new Hit("http://www.test.com/docs/1.2.3/TestDiffLengthVersion/1.2/rest", "TestDiffLengthVersion", 1.0F, 2.0F));
                    result.add(new Hit("http://www.test.com/docs/2/TestDiffLengthVersion/1.2/rest", "TestDiffLengthVersion", 1.0F, 1.0F));
                    result.add(new Hit("http://www.test.com/docs/1.2.3/TestBothDifferent/1.2/rest", "TestBothDifferent", 1.0F, 2.5F));
                    result.add(new Hit("http://www.test.com/docs/1.2.4/TestBothDifferent/1.3/rest", "TestBothDifferent", 1.0F, 2.0F));
                    result.add(new Hit("http://www.domainone.com/docs/1.2.3/TestDiffDomain/1.2/rest", "TestDiffDomain", 1.0F, 1.5F));
                    result.add(new Hit("http://www.domaintwo.com/docs/1.2.3/TestDiffDomain/1.2/rest", "TestDiffDomain", 1.0F, 1.0F));
                } 
                return result;
            }

        };
        
        BatchQueryReply result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testCompactHitInfos");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    private JsonNode getExpectedResult(String testKey) {
        return testData.get(testKey);
    }
    
    private JsonNode getJsonNode(Object obj) throws JsonParseException, IOException {
        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createJsonParser(getJsonString(obj));
        parser.setCodec(new ObjectMapper());
        return parser.readValueAsTree();
    }
    
    private String getJsonString(Object obj) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        StringWriter writer = new StringWriter();
        
        JsonGenerator jsonGenerator = mapper.getJsonFactory().createJsonGenerator(writer);
        jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

        mapper.writeValue(jsonGenerator, obj);
        return writer.toString();
    }

}
