package ca.ubc.cs.reverb.indexer.messages;

import java.util.List;

public class CodeQueryRequest implements IndexerMessage {
    public CodeQueryRequest() {
        
    }
    
    public CodeQueryRequest(List<CodeElement> codeElements) {
        this.codeElements = codeElements;
    }
    
    public List<CodeElement> codeElements;
}    

