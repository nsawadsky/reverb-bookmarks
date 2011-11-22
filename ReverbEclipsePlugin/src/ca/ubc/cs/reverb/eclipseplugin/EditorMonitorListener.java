package ca.ubc.cs.reverb.eclipseplugin;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;

public interface EditorMonitorListener {
    void handleBatchQueryResult(BatchQueryResult result);
}
