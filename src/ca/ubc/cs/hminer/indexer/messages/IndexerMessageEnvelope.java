package ca.ubc.cs.hminer.indexer.messages;

public class IndexerMessageEnvelope {
    public IndexerMessageEnvelope(long requestId, IndexerMessage payload) {
        
    }
    
    public long requestId;
    public IndexerMessage message;
}
