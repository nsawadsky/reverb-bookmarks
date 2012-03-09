package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class CodeQueryResult {
    public CodeQueryResult() {
    }
    
    public CodeQueryResult(List<Location> locations, String displayText, List<CodeElement> queryCodeElements) {
        this.locations = locations;
        this.displayText = displayText;
        this.queryCodeElements = queryCodeElements;
    }
    
    public List<Location> locations = new ArrayList<Location>();
    public String displayText;
    public List<CodeElement> queryCodeElements = new ArrayList<CodeElement>();
}
