package ca.ubc.cs.reverb.eclipseplugin;

import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;

public interface EditorMonitorListener {
    void onCodeQueryReply(CodeQueryReply reply);
}
