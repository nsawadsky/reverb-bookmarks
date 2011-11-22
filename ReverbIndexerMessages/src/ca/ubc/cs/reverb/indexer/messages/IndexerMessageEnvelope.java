package ca.ubc.cs.reverb.indexer.messages;

public class IndexerMessageEnvelope {
    public IndexerMessageEnvelope() {
        
    }
    
    public IndexerMessageEnvelope(String clientRequestId, IndexerMessage payload) {
        this.clientRequestId = clientRequestId;
        this.message = payload;
    }
    
    public IndexerMessage message;
    public String clientRequestId;
}
