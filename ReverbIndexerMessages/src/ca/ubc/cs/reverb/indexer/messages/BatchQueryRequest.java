package ca.ubc.cs.reverb.indexer.messages;

import java.util.List;

public class BatchQueryRequest implements IndexerMessage {
    public BatchQueryRequest() {
        
    }
    
    public BatchQueryRequest(List<IndexerQuery> queries) {
        this.queries = queries;
    }
    
    public List<IndexerQuery> queries;
}    

