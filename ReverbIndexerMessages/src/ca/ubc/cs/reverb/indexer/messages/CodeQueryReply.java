package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CodeQueryReply extends IndexerReply {
    public CodeQueryReply() {
    }
    
    public CodeQueryReply(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public CodeQueryReply(boolean errorOccurred, String errorMessage) {
        super(errorOccurred, errorMessage);
        timestamp = new Date().getTime();
    }

    public List<CodeQueryResult> queryResults = new ArrayList<CodeQueryResult>();
    public List<CodeElementError> errorElements = new ArrayList<CodeElementError>();
    
    /**
     * Timestamp when the results were generated.
     */
    public long timestamp;
}
