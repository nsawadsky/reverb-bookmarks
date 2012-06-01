package ca.ubc.cs.reverb.indexer.messages;

import java.util.ArrayList;
import java.util.List;

public class LogClientEventRequest implements IndexerMessage {
    public LogClientEventRequest() { }
    public LogClientEventRequest(String clientEventType, List<String> attributes) {
        this.eventType = clientEventType;
        this.attributes = attributes;
    }
    
    public String eventType;
    public List<String> attributes = new ArrayList<String>();
}
