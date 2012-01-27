package ca.ubc.cs.reverb.indexer.messages;

public class LogClickRequest implements IndexerMessage {
    public LogClickRequest() { }
    public LogClickRequest(Location location) {
        this.location = location;
    }
    
    public Location location;
}
