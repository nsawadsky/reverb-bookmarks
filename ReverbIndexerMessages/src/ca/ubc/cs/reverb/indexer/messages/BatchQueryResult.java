package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class BatchQueryResult implements IndexerMessage {
    public BatchQueryResult() {
    }
    
    public List<QueryResult> queryResults = new ArrayList<QueryResult>();
}
