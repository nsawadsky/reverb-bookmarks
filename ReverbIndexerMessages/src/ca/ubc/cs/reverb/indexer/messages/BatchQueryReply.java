package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class BatchQueryReply extends IndexerReply {
    public BatchQueryReply() {
    }
    
    public List<QueryResult> queryResults = new ArrayList<QueryResult>();
}
