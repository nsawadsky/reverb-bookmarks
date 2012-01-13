package ca.ubc.cs.reverb.indexer;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;
import org.junit.Before;
import org.junit.Test;

import ca.ubc.cs.reverb.indexer.messages.CodeElement;
import ca.ubc.cs.reverb.indexer.messages.CodeElementError;
import ca.ubc.cs.reverb.indexer.messages.CodeElementType;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;


public class QueryBuilderTest {
    private JsonNode testData;
    
    @Before
    public void setup() throws JsonParseException, IOException {
        InputStream testDataStream = QueryBuilderTest.class.getResourceAsStream("QueryBuilderTestData.json");

        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createJsonParser(testDataStream);
        parser.setCodec(new ObjectMapper());
        testData = parser.readValueAsTree();
    }
    
    @Test
    public void testTypeDeclRef() throws JsonParseException, IOException {
        QueryBuilder builder = new QueryBuilder(Arrays.asList( new CodeElement[] { 
                new CodeElement(CodeElementType.TYPE_REF, "mypackage", "Myclassref", null),
                new CodeElement(CodeElementType.TYPE_DECL, "mypackage", "Myclassdecl", null),
                new CodeElement(CodeElementType.TYPE_DECL, "mypackage", "MyclassdeclNores", null),
                new CodeElement(CodeElementType.TYPE_DECL, null, "Myclassdeclnopackage", null),
                new CodeElement(CodeElementType.TYPE_DECL, null, "MyclassdeclnopackageNores", null),
                }));
        builder.buildQueries();
        List<IndexerQuery> result = builder.getQueries();
        JsonNode expected = getExpectedResult("testTypeDeclRef");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testMethodDeclCall() throws JsonParseException, IOException {
        QueryBuilder builder = new QueryBuilder(Arrays.asList( new CodeElement[] { 
                new CodeElement(CodeElementType.METHOD_DECL, "mypackage", "Myclass", "mymethoddecl"),
                new CodeElement(CodeElementType.METHOD_CALL, "mypackage", "Myclass", "mymethodcall"),
                new CodeElement(CodeElementType.METHOD_CALL, null, "MyclassNores", "mymethodcall"),
                new CodeElement(CodeElementType.METHOD_CALL, null, null, "mymethodcall"),
                new CodeElement(CodeElementType.METHOD_CALL, null, null, "mymethodcallNores"),
                }));
        builder.buildQueries();
        List<IndexerQuery> result = builder.getQueries();
        JsonNode expected = getExpectedResult("testMethodDeclCall");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testStaticFieldRef() throws JsonParseException, IOException {
        QueryBuilder builder = new QueryBuilder(Arrays.asList( new CodeElement[] { 
                new CodeElement(CodeElementType.STATIC_METHOD_CALL, "mypackage", "Myclass", "mymethodcall"),
                new CodeElement(CodeElementType.STATIC_FIELD_REF, "mypackage", "Myclass", "MY_FIELD_REF"),
                new CodeElement(CodeElementType.STATIC_FIELD_REF, null, "Myclass", "MY_FIELD_REF"),
                }));
        builder.buildQueries();
        List<IndexerQuery> result = builder.getQueries();
        JsonNode expected = getExpectedResult("testStaticFieldRef");
        assertEquals("formatted actual: " + getJsonString(result), expected, getJsonNode(result));
    }

    @Test
    public void testErrorElements() throws JsonParseException, IOException { 
        QueryBuilder builder = new QueryBuilder(Arrays.asList( new CodeElement[] { 
                new CodeElement(CodeElementType.TYPE_DECL, null, "Myclass", null),
                }));
        builder.buildQueries();
        List<CodeElementError> result = builder.getErrorElements();
        JsonNode expected = getExpectedResult("testErrorElements");
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
