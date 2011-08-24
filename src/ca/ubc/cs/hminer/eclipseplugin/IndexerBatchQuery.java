package ca.ubc.cs.hminer.eclipseplugin;

import java.util.List;

public class IndexerBatchQuery {
    private List<String> queryStrings;
    
    public IndexerBatchQuery(List<String> queryStrings) {
        this.queryStrings = queryStrings;
    }
    
    public List<String> getQueryStrings() {
        return queryStrings;
    }
}
