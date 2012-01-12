package ca.ubc.cs.reverb.eclipseplugin;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;

public interface EditorMonitorListener {
    void onBatchQueryReply(BatchQueryReply reply);
    void onCodeQueryReply(CodeQueryReply reply);
}
