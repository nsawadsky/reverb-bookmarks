package ca.ubc.cs.periscope.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class BatchQueryResult implements IndexerMessage {
    public BatchQueryResult() {
    }
    
    public List<QueryResult> queryResults = new ArrayList<QueryResult>();
}
