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

import ca.ubc.cs.reverb.indexer.WebPageSearcher.Hit;
import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.study.StudyDataCollector;


public class WebPageSearcherTest {
    private JsonNode testData;
    private IndexerConfig config;
    private StudyDataCollector collector;
    
    @Before
    public void setup() throws JsonParseException, IOException, IndexerException {
        InputStream testDataStream = WebPageSearcherTest.class.getResourceAsStream("WebPageSearcherTestData.json");
        
        try {
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createJsonParser(testDataStream);
            parser.setCodec(new ObjectMapper());
            testData = parser.readValueAsTree();

            config = new IndexerConfig();
            collector = new StudyDataCollector(config);
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
        
        WebPageSearcher searcher = new WebPageSearcher(config, null, null, collector) {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override 
            protected long getCurrentTime() {
                return 1000L;
            }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString.equals("QueryA")) {
                    result.add(getHit(1L, "UrlA_A", "UrlA_A", 2.0F, 1.0F));
                    result.add(getHit(2L, "UrlB_B", "UrlB_B", 1.0F, 1.0F));
                } else if (queryString.equals("QueryB")) {
                    result.add(getHit(3L, "UrlB_A", "UrlB_A", 1.0F, 1.0F));
                    result.add(getHit(2L, "UrlB_B", "UrlB_B", 2.0F, 1.0F));
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
        
        WebPageSearcher searcher = new WebPageSearcher(config, null, null, collector) {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override 
            protected long getCurrentTime() {
                return 1000L;
            }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString.equals("QueryA")) {
                    Hit hit = getHit(1L, "UrlA_A", "UrlA_A", 3.0F, 1.0F);
                    result.add(hit);
                } else if (queryString.equals("QueryB")) {
                    Hit hit = getHit(2L, "UrlB_A", "UrlB_A", 2.0F, 2.0F);
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
        
        WebPageSearcher searcher = new WebPageSearcher(config, null, null, collector) {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override 
            protected long getCurrentTime() {
                return 1000L;
            }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString.equals("QueryA")) {
                    result.add(getHit(1L, "UrlA_A", "UrlA_A", 4.0F, 1.0F));
                    result.add(getHit(2L, "UrlB_A", "UrlB_A", 1.1F, 1.0F));
                    result.add(getHit(3L, "UrlB_B", "UrlB_B", 1.0F, 1.0F));
                } else if (queryString.equals("QueryB")) {
                    result.add(getHit(2L, "UrlB_A", "UrlB_A", 2.0F, 1.0F));
                    result.add(getHit(3L, "UrlB_B", "UrlB_B", 2.0F, 1.0F));
                } else if (queryString.equals("QueryC")) {
                    result.add(getHit(4L, "UrlC_A", "UrlC_A", 1.1F, 1.0F));
                    result.add(getHit(5L, "UrlC_B", "UrlC_B", 1.0F, 1.0F));
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
        
        WebPageSearcher searcher = new WebPageSearcher(config, null, null, collector) {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override 
            protected long getCurrentTime() {
                return 1000L;
            }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString.equals("QueryA")) {
                    result.add(getHit(1L, "http://www.test.com/docs/1.2.3/TestFirstDifferent/1.2/rest", "TestFirstDifferent123", 1.0F, 2.0F));
                    result.add(getHit(2L, "http://www.test.com/docs/1.2.4/TestFirstDifferent/1.2/rest", "TestFirstDifferent124", 1.0F, 2.0F));
                    result.add(getHit(3L, "http://www.test.com/docs/1.2.5/TestFirstDifferent/1.2/rest", "TestFirstDifferent125", 1.0F, 2.0F));
                    result.add(getHit(4L, "http://www.test.com/docs/1.2.4/TestFirstDifferentReverse/1.2/rest", "TestFirstDifferentReverse124", 1.0F, 2.5F));
                    result.add(getHit(5L, "http://www.test.com/docs/1.2.3/TestFirstDifferentReverse/1.2/rest", "TestFirstDifferentReverse123", 1.0F, 2.0F));
                    result.add(getHit(6L, "http://www.test.com/docs/1.2.3/TestSecondDifferent/1.2/rest", "TestSecondDifferent12", 1.0F, 2.0F));
                    result.add(getHit(7L, "http://www.test.com/docs/1.2.3/TestSecondDifferent/2.2/rest", "TestSecondDifferent22", 1.0F, 2.0F));
                    result.add(getHit(8L, "http://www.test.com/docs/1.2.3/TestEndsWithVersion/1.2", "TestEndsWithVersion12", 1.0F, 2.0F));
                    result.add(getHit(9L, "http://www.test.com/docs/1.2.3/TestEndsWithVersion/2.2", "TestEndsWithVersion22", 1.0F, 1.5F));
                    result.add(getHit(10L, "http://www.test.com/docs/1.2.3/TestDiffLengthVersion/1.2/rest", "TestDiffLengthVersion123", 1.0F, 2.0F));
                    result.add(getHit(11L, "http://www.test.com/docs/2/TestDiffLengthVersion/1.2/rest", "TestDiffLengthVersion2", 1.0F, 1.0F));
                    result.add(getHit(12L, "http://www.test.com/docs/1.2.3/TestBothDifferent/1.2/rest", "TestBothDifferent123", 1.0F, 2.5F));
                    result.add(getHit(13L, "http://www.test.com/docs/1.2.4/TestBothDifferent/1.3/rest", "TestBothDifferent124", 1.0F, 2.0F));
                    result.add(getHit(14L, "http://www.domainone.com/docs/1.2.3/TestDiffDomain/1.2/rest", "TestDiffDomainOne", 1.0F, 1.5F));
                    result.add(getHit(15L, "http://www.domaintwo.com/docs/1.2.3/TestDiffDomain/1.2/rest", "TestDiffDomainTwo", 1.0F, 1.0F));
                }
                return result;
            }

        };
        
        BatchQueryReply result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testCompactHitInfos");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testCompactHitInfosSimilar() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("QueryA", "QueryA"));
        
        WebPageSearcher searcher = new WebPageSearcher(config, null, null, collector) {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override 
            protected long getCurrentTime() {
                return 1000L;
            }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString.equals("QueryA")) {
                    result.add(getHit(15L, "http://www.diffdomainone.com/docs/TestSimilar1/rest", "TestSimilar", 1.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomaintwo.com/docs/TestSimilar2/rest", "TestSimilar", 1.0F, 2.0F));
                    result.add(getHit(15L, "http://www.diffdomainone.com/docs1/TestSimilarShort/", "TestSimilarShort", 1.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomaintwo.com/docs2/TestSimilarShort/", "TestSimilarShort", 1.0F, 2.0F));
                } 
                return result;
            }

        };
        
        BatchQueryReply result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testCompactHitInfosSimilar");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testCompactHitInfosDifferent() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("QueryA", "QueryA"));
        inputQueries.add(new IndexerQuery("QueryB", "QueryB"));
        
        WebPageSearcher searcher = new WebPageSearcher(config, null, null, collector) {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            @Override 
            protected long getCurrentTime() {
                return 1000L;
            }
            
            @Override
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString.equals("QueryA")) {
                    result.add(getHit(15L, "http://www.diffdomainone.com/docs/TestDiffQuery/rest", "TestDiffQuery", 10.0F, 1.0F));
                } else if (queryString.equals("QueryB")) {
                    result.add(getHit(15L, "http://www.diffdomaintwo.com/docs/TestDiffQuery/rest", "TestDiffQuery", 10.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomainone.com/docs/TestDiffTitle/rest", "TestDiffTitleOne", 9.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomaintwo.com/docs/TestDiffTitle/rest", "TestDiffTitleTwo", 9.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomainone.com/docs/TestDiffRelevance/rest", "TestDiffRelevance", 8.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomaintwo.com/docs/TestDiffRelevance/rest", "TestDiffRelevance", 7.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomainone.com/docs/TestDiffLastSegment/restone", "TestDiffLastSegment", 6.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomaintwo.com/docs/TestDiffLastSegment/resttwo", "TestDiffLastSegment", 6.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomainone.com/docs/TestDiffLastSegmentShortOne/", "TestDiffLastSegmentShort", 5.0F, 1.0F));
                    result.add(getHit(15L, "http://www.diffdomaintwo.com/docs/TestDiffLastSegmentShortTwo/", "TestDiffLastSegmentShort", 5.0F, 1.0F));
                } 
                return result;
            }

        };
        
        BatchQueryReply result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testCompactHitInfosDifferent");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    private Hit getHit(long id, String url, String title, float relevance, float frecencyBoost) {
        Hit result = new Hit(url, title, relevance, frecencyBoost);
        result.locationInfo = getLocationInfo(id, url);
        return result;
    }
    
    private LocationInfo getLocationInfo(long id, String url) {
        return new LocationInfo(id, url, 1000L, 1, 1.0F, false, false);
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
