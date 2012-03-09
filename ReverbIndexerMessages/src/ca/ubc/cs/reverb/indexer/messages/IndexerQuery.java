package ca.ubc.cs.reverb.indexer.messages;

public class IndexerQuery {
    public String queryString;
    public String queryClientInfo;
    
    public IndexerQuery() { }

    public IndexerQuery(String queryString, String queryClientInfo) {
        this.queryString = queryString;
        this.queryClientInfo = queryClientInfo;
    }
}
