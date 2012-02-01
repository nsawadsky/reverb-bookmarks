package ca.ubc.cs.reverb.eclipseplugin;

import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;

public interface EditorMonitorListener {
    void onCodeQueryReply(CodeQueryReply reply);
    
    /**
     * An interaction event occurred (key pressed, mouse button pressed, viewport changed) in a Java code editor.
     */
    void onInteractionEvent(long timeMsecs);
}
