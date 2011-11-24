package ca.ubc.cs.reverb.indexer.messages;

public class DeleteLocationRequest implements IndexerMessage {
    public DeleteLocationRequest() { }
    
    public DeleteLocationRequest(String url) {
        this.url = url;
    }

    public String url;
    
}
