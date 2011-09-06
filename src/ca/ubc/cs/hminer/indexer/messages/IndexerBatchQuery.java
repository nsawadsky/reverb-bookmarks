package ca.ubc.cs.hminer.indexer.messages;

import java.util.List;

public class IndexerBatchQuery implements IndexerMessage {
    public IndexerBatchQuery() {
        
    }
    
    public IndexerBatchQuery(List<String> queryStrings) {
        this.queryStrings = queryStrings;
    }
    
    public List<String> queryStrings;
}    

