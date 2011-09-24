package ca.ubc.cs.periscope.indexer.messages;

public class IndexerMessageEnvelope {
    public IndexerMessageEnvelope() {
        
    }
    
    public IndexerMessageEnvelope(IndexerMessage payload) {
        this.message = payload;
    }
    
    public IndexerMessage message;
}
