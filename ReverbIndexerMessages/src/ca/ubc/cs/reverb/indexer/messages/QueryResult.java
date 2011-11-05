package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    public QueryResult() {
    }
    
    public QueryResult(List<String> queries, List<Location> locations) {
        this.queries = queries;
        this.locations = locations;
    }
    
    public List<String> queries = new ArrayList<String>();
    public List<Location> locations = new ArrayList<Location>();
}
