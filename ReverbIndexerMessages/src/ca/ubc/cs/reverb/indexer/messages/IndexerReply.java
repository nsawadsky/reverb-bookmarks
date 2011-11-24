package ca.ubc.cs.reverb.indexer.messages;

public class IndexerReply implements IndexerMessage {
    public IndexerReply() { }
    
    public IndexerReply(boolean errorOccurred, String errorMessage) {
        this.errorOccurred = errorOccurred;
        this.errorMessage = errorMessage;
    }
    
    public boolean errorOccurred = false;
    
    public String errorMessage = null;
    
}
