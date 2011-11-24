package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class UpdatePageInfoRequest implements IndexerMessage {
    public UpdatePageInfoRequest() {
        visitTimes = new ArrayList<Long>();
    }
    
    public UpdatePageInfoRequest(String url, String html) {
        this.url = url;
        this.html = html;
        visitTimes = new ArrayList<Long>();
    }
    
    public List<Long> visitTimes;
    public String url;
    public String html;
}
