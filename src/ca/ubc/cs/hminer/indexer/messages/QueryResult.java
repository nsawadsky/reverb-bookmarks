package ca.ubc.cs.hminer.indexer.messages;

import java.util.List;

public class QueryResult {
    public QueryResult(String query, List<Location> locations) {
        this.query = query;
        this.locations = locations;
    }
    
    public String query;
    public List<Location> locations;
}
