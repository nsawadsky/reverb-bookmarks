package ca.ubc.cs.hminer.indexer.messages;

public class PageInfo implements IndexerMessage {
    public PageInfo() {
    }
    
    public PageInfo(String url, String html) {
        this.url = url;
        this.html = html;
    }
    
    public String url;
    public String html;
}
