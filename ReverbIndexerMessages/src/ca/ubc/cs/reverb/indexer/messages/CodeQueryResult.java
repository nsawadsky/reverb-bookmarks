package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class CodeQueryResult {
    public CodeQueryResult() {
    }
    
    public CodeQueryResult(List<CodeElement> codeElements, List<Location> locations, String displayText) {
        this.codeElements = codeElements;
        this.locations = locations;
        this.displayText = displayText;
    }
    
    public List<CodeElement> codeElements = new ArrayList<CodeElement>();
    public List<Location> locations = new ArrayList<Location>();
    public String displayText;
}
