package ca.ubc.cs.reverb.eclipseplugin;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;

@RunWith(Parameterized.class)
public class QueryBuilderASTVisitorTest {
    private JsonNode testData;
    private String testFileName;
    
    @Parameters
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(new Object[][]{ 
                { "src/testpackage/FieldRefs.java" },
                { "src/testpackage/ComplexTypeRefs.java" }, 
                { "src/testpackage/Enumdecl.java" },
                { "src/testpackage/EnumdeclNores.java" },
                { "src/testpackage/Classdecl.java" },
                { "src/testpackage/ClassdeclNores.java" },
                { "src/testpackage/Methoddecl.java" },
                { "src/testpackage/MethodInvoc.java" },
                { "src/testpackage/PrimitiveRefs.java" },
                });
    }
    
    public QueryBuilderASTVisitorTest(String testFileName) throws JsonParseException, IOException {
        this.testFileName = testFileName;
    }
    
    @Before
    public void setup() throws JsonParseException, IOException {
        InputStream testDataStream = QueryBuilderASTVisitorTest.class.getResourceAsStream("TestData.json");

        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createJsonParser(testDataStream);
        parser.setCodec(new ObjectMapper());
        testData = parser.readValueAsTree();
    }
    
    @Test
    public void runTest() throws IOException {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IProject project = workspace.getRoot().getProject("EclipsePluginTestProject");
        assertNotNull("Cannot find EclipsePluginTestProject", project);
        IFile testFile = project.getFile(testFileName);
        IJavaElement fileElement = JavaCore.create(testFile);
        assertNotNull("Cannot find file: " + testFileName, fileElement);
        
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource((ICompilationUnit)fileElement);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        CompilationUnit compileUnit = (CompilationUnit)parser.createAST(null);
        QueryBuilderASTVisitor visitor = new QueryBuilderASTVisitor(0, 10000);
        compileUnit.accept(visitor);
        
        List<IndexerQuery> queries = visitor.getQueries();

        JsonNode expected = getExpectedResult(testFileName);
        assertEquals("formatted actual: " + getJsonString(queries), expected, getJsonNode(queries));

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
