package ca.ubc.cs.reverb.indexer.messages;

import java.util.List;

public class CodeQueryReply implements IndexerMessage {
    public CodeQueryReply() { }
    
    public CodeQueryReply(List<CodeQueryResult> results) {
        this.results = results;
    }
    
    public List<CodeQueryResult> results;
}
