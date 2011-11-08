package ca.ubc.cs.reverb.indexer.messages;

import java.util.List;

public class IndexerBatchQuery implements IndexerMessage {
    public IndexerBatchQuery() {
        
    }
    
    public IndexerBatchQuery(List<IndexerQuery> queries) {
        this.queries = queries;
    }
    
    public List<IndexerQuery> queries;
}    

