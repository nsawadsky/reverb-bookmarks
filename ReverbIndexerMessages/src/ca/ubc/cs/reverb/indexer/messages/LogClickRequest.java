package ca.ubc.cs.reverb.indexer.messages;

public class LogClickRequest implements IndexerMessage {
    public LogClickRequest() { }
    public LogClickRequest(Location location, long resultGenTimestamp) {
        this.location = location;
        this.resultGenTimestamp = resultGenTimestamp;
    }
    
    public Location location;
    public long resultGenTimestamp;
}
