package ca.ubc.cs.hminer.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class BatchQueryResult implements IndexerMessage {
    public List<QueryResult> queryResults = new ArrayList<QueryResult>();
}
