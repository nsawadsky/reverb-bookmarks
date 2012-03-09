package ca.ubc.cs.reverb.indexer.messages;

import java.util.Map;
import java.util.TreeMap;

public class IndexerQuery {
    public String queryString;
    public Map<String, String> queryClientInfo = new TreeMap<String, String>();
    
    public IndexerQuery() { }

    public IndexerQuery(String queryString) {
        this.queryString = queryString;
    }
}
