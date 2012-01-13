package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class CodeQueryReply extends IndexerReply {
    public CodeQueryReply() {
    }
    
    public CodeQueryReply(boolean errorOccurred, String errorMessage) {
        super(errorOccurred, errorMessage);
    }

    public List<CodeQueryResult> queryResults = new ArrayList<CodeQueryResult>();
    public List<CodeElementError> errorElements = new ArrayList<CodeElementError>();
}
