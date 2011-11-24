package ca.ubc.cs.reverb.eclipseplugin;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;

public interface EditorMonitorListener {
    void onBatchQueryResult(BatchQueryReply result);
}
