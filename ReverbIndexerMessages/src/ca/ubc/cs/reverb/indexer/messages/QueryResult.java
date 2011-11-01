package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    public QueryResult() {
    }
    
    public QueryResult(String query, List<Location> locations) {
        this.query = query;
        this.locations = locations;
    }
    
    public String query;
    public List<Location> locations = new ArrayList<Location>();
}
