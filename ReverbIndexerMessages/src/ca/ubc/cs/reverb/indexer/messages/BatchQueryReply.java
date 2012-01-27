package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BatchQueryReply extends IndexerReply {
    public BatchQueryReply() {
    }
    
    public BatchQueryReply(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public BatchQueryReply(boolean errorOccurred, String errorMessage) {
        super(errorOccurred, errorMessage);
        timestamp = new Date().getTime();
    }

    public List<QueryResult> queryResults = new ArrayList<QueryResult>();

    /**
     * Timestamp when the results were generated.
     */
    public long timestamp;
}
