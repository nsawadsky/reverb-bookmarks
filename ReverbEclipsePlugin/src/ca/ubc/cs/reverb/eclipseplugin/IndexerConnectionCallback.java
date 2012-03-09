package ca.ubc.cs.reverb.eclipseplugin;

import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;

public interface IndexerConnectionCallback {
    void onIndexerMessage(IndexerMessage message, Object clientInfo);
    void onIndexerError(String message, Throwable t);
}   
