package ca.ubc.cs.hminer.eclipseplugin;

public interface IndexerConnectionCallback<MessageClass> {
    public abstract void handleMessage(MessageClass msg); 
}
