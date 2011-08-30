package ca.ubc.cs.hminer.eclipseplugin;

import ca.ubc.cs.hminer.indexer.messages.IndexerMessage;

public interface IndexerConnectionCallback<MessageClass extends IndexerMessage> {
    public abstract void handleMessage(MessageClass msg); 
}
