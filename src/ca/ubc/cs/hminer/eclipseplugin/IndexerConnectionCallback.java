package ca.ubc.cs.hminer.eclipseplugin;

public interface IndexerConnectionCallback<MessageType> {
    public void handleMessage(MessageType msg); 
}
