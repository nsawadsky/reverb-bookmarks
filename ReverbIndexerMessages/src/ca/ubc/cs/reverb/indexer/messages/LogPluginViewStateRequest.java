package ca.ubc.cs.reverb.indexer.messages;

public class LogPluginViewStateRequest implements IndexerMessage {
    public LogPluginViewStateRequest() { }
    public LogPluginViewStateRequest(boolean isViewOpen) {
        this.isViewOpen = isViewOpen;
    }
    
    public boolean isViewOpen;
}
