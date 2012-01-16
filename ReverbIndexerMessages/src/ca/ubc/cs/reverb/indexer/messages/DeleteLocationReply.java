package ca.ubc.cs.reverb.indexer.messages;

public class DeleteLocationReply extends IndexerReply {
    public DeleteLocationReply() { } 
    
    public DeleteLocationReply(boolean errorOccurred, String errorMessage) { 
        super(errorOccurred, errorMessage);
    }
}
