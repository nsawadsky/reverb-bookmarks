package ca.ubc.cs.periscope.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class PageInfo implements IndexerMessage {
    public PageInfo() {
        visitTimes = new ArrayList<Long>();
    }
    
    public PageInfo(String url, String html) {
        this.url = url;
        this.html = html;
        visitTimes = new ArrayList<Long>();
    }
    
    public List<Long> visitTimes;
    public String url;
    public String html;
}
