package ca.ubc.cs.hminer.indexer;

public class QueryPipeListener {
    private IndexerConfig config;
    private WebPageSearcher searcher;
    
    public QueryPipeListener(IndexerConfig config, WebPageSearcher searcher) {
        this.config = config;
        this.searcher = searcher;
    }
    
    public void start() {
    
    }

}
