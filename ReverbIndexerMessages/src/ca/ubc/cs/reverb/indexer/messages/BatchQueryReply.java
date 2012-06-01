package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class BatchQueryReply extends IndexerReply {
    public BatchQueryReply() { }
    
    public BatchQueryReply(long resultGenTimestamp) {
        this.resultGenTimestamp = resultGenTimestamp;
    }
    
    public BatchQueryReply(boolean errorOccurred, String errorMessage) {
        super(errorOccurred, errorMessage);
    }

    /**
     * Timestamp when the result set was generated.
     */
    public long resultGenTimestamp;
    public List<QueryResult> queryResults = new ArrayList<QueryResult>();
}
