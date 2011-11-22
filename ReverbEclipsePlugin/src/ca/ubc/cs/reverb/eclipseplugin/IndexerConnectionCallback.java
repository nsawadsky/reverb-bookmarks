package ca.ubc.cs.reverb.eclipseplugin;

import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;

public interface IndexerConnectionCallback {
    void handleMessage(IndexerMessage message, Object clientInfo);
    void handleError(String message, Throwable t);
}   
