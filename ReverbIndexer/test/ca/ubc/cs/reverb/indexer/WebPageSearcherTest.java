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

import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;


public class WebPageSearcherTest {
    private JsonNode testData;
    
    @Before
    public void setup() throws JsonParseException, IOException {
        InputStream testDataStream = WebPageSearcherTest.class.getResourceAsStream("WebPageSearcherTestData.json");

        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createJsonParser(testDataStream);
        parser.setCodec(new ObjectMapper());
        testData = parser.readValueAsTree();
    }
    
    @Test
    public void testFilterGroupAndReorder() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("Query1", "Query1"));
        inputQueries.add(new IndexerQuery("Query1", "Query1"));
        inputQueries.add(new IndexerQuery("Query2", "Query2"));
        
        WebPageSearcher searcher = new WebPageSearcher() {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString == "Query1") {
                    result.add(new Hit("Url1_1", "Url1_1", 2.0F));
                    result.add(new Hit("Url2_2", "Url2_2", 1.0F));
                } else if (queryString == "Query2") {
                    result.add(new Hit("Url2_1", "Url2_1", 1.0F));
                    result.add(new Hit("Url2_2", "Url2_2", 2.0F));
                } 
                return result;
            }

        };
        
        BatchQueryResult result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testFilterGroupAndReorder");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testFrecencyBoost() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("Query1", "Query1"));
        inputQueries.add(new IndexerQuery("Query2", "Query2"));
        
        WebPageSearcher searcher = new WebPageSearcher() {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString == "Query1") {
                    Hit hit = new Hit("Url1_1", "Url1_1", 3.0F);
                    hit.frecencyBoost = 1.0F;
                    result.add(hit);
                } else if (queryString == "Query2") {
                    Hit hit = new Hit("Url2_1", "Url2_1", 2.0F);
                    hit.frecencyBoost = 2.0F;
                    result.add(hit);
                } 
                return result;
            }

        };
        
        BatchQueryResult result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testFrecencyBoost");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testMerge() throws IndexerException, IOException {
        List<IndexerQuery> inputQueries = new ArrayList<IndexerQuery>();
        inputQueries.add(new IndexerQuery("Query1", "Query1"));
        inputQueries.add(new IndexerQuery("Query2", "Query2"));
        inputQueries.add(new IndexerQuery("Query3", "Query3"));
        
        WebPageSearcher searcher = new WebPageSearcher() {
            protected IndexSearcher getNewIndexSearcher() { return null; }
            
            protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults) throws IndexerException {
                List<Hit> result = new ArrayList<Hit>();
                if (queryString == "Query1") {
                    result.add(new Hit("Url1_1", "Url1_1", 4.0F));
                    result.add(new Hit("Url2_1", "Url2_1", 1.0F));
                    result.add(new Hit("Url2_2", "Url2_2", 1.0F));
                } else if (queryString == "Query2") {
                    result.add(new Hit("Url2_1", "Url2_1", 2.0F));
                    result.add(new Hit("Url2_2", "Url2_2", 2.0F));
                } else if (queryString == "Query3") {
                    result.add(new Hit("Url3_1", "Url3_1", 1.0F));
                    result.add(new Hit("Url3_2", "Url3_2", 1.0F));
                } 
                return result;
            }

        };
        
        BatchQueryResult result = searcher.performSearch(inputQueries);
        JsonNode expected = getExpectedResult("testMerge");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    private JsonNode getExpectedResult(String testKey) {
        return testData.get(testKey);
    }
    
    private JsonNode getJsonNode(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(obj);
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
