package ca.ubc.cs.reverb.indexer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;

import ca.ubc.cs.reverb.indexer.messages.CodeElement;

/**
 * Class must be thread-safe.
 * 
 * This class is serialized and deserialized using Jackson JSON object mapping.  Have
 * to be very careful to apply correct annotations/attributes (i.e JsonProperty/transient) 
 * when adding new fields, depending on whether you want field to be serialized or not.
 */
public class BlockedTypes {
    @JsonProperty()
    private List<CodeElement> blockedCodeElements = new ArrayList<CodeElement>();

    private transient String blockedTypesPath;
    
    public BlockedTypes() { }

    public BlockedTypes(String blockedTypesPath) {
        this.blockedTypesPath = blockedTypesPath;
    }
    
    public static BlockedTypes load(String blockedTypesPath) throws IndexerException {
        try {
            File blockedTypesFile = new File(blockedTypesPath);
            if (!blockedTypesFile.exists()) {
                return new BlockedTypes(blockedTypesPath);
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            BlockedTypes blockedTypes = mapper.readValue(blockedTypesFile, BlockedTypes.class);
            blockedTypes.blockedTypesPath = blockedTypesPath;
            return blockedTypes;
        } catch (Exception e) {
            throw new IndexerException("Error loading blocked types: " + e, e);
        }
        
    }

    public synchronized void addBlockedElement(CodeElement codeElement) throws IndexerException {
        if (codeElement.packageName == null && codeElement.className == null) {
            throw new IndexerException("Type must be fully resolved to be blocked");
        }
        for (CodeElement element: blockedCodeElements) {
            if (codeElement.packageName.equals(element.packageName) && 
                    codeElement.className.equals(element.className)) {
                throw new IndexerException(codeElement.packageName + "." + codeElement.className + " is already blocked");
            }
        }
        blockedCodeElements.add(codeElement);
        save();
    }
    
    public synchronized boolean checkIsBlocked(CodeElement codeElement) {
        if (codeElement.packageName != null && codeElement.className != null) {
            for (CodeElement element: blockedCodeElements) {
                if (codeElement.packageName.equals(element.packageName) && codeElement.className.equals(element.className)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public synchronized List<CodeElement> trimBlockedElements(List<CodeElement> codeElements) {
        List<CodeElement> result = new ArrayList<CodeElement>();
        for (CodeElement codeElement: codeElements) {
            if (!checkIsBlocked(codeElement)) {
                result.add(codeElement);
            }
        }
        return result;
    }
    
    private void save() throws IndexerException {
        JsonGenerator jsonGenerator = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            jsonGenerator = mapper.getJsonFactory().createJsonGenerator(new File(blockedTypesPath), 
                    JsonEncoding.UTF8);
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

            mapper.writeValue(jsonGenerator, this);
        } catch (Exception e) {
            throw new IndexerException("Error saving blocked types: " + e, e);
        } finally {
            if (jsonGenerator != null) {
                try { 
                    jsonGenerator.close();
                } catch (IOException e) { } 
            }
        }
    }
    
}
