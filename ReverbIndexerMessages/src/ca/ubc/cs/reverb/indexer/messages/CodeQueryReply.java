package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class CodeQueryReply extends IndexerReply {
    public CodeQueryReply() {
    }
    
    public CodeQueryReply(long resultGenTimestamp) {
        this.resultGenTimestamp = resultGenTimestamp;
    }
    
    public CodeQueryReply(boolean errorOccurred, String errorMessage) {
        super(errorOccurred, errorMessage);
    }

    /**
     * Timestamp when the result set was generated.
     */
    public long resultGenTimestamp;
    
    public List<CodeQueryResult> queryResults = new ArrayList<CodeQueryResult>();
    public List<CodeElementError> errorElements = new ArrayList<CodeElementError>();
}
