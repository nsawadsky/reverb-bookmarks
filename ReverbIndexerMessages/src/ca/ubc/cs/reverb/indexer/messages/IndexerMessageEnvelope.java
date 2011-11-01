package ca.ubc.cs.reverb.indexer.messages;

public class IndexerMessageEnvelope {
    public IndexerMessageEnvelope() {
        
    }
    
    public IndexerMessageEnvelope(IndexerMessage payload) {
        this.message = payload;
    }
    
    public IndexerMessage message;
}
