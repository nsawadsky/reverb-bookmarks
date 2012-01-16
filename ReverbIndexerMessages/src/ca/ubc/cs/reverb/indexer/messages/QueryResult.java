package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    public QueryResult() {
    }
    
    public QueryResult(List<IndexerQuery> indexerQueries, List<Location> locations) {
        this.indexerQueries = indexerQueries;
        this.locations = locations;
    }
    
    public List<IndexerQuery> indexerQueries = new ArrayList<IndexerQuery>();
    public List<Location> locations = new ArrayList<Location>();
}
