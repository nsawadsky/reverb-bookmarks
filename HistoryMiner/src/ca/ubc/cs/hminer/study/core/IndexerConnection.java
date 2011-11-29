package ca.ubc.cs.hminer.study.core;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.UpdatePageInfoRequest;

import xpnp.XpNamedPipe;

// It's important that we don't have a field of type XpNamedPipe -- otherwise when this class is 
// loaded, then the XpNamedPipe class would be loaded immediately as well.  Then we would 
// need to bundle a platform-specific XpNamedPipeJni.dll with each deployment package.  That 
// would add complexity to the deployment process, when we really only need the indexing functionality
// for testing purposes.
public class IndexerConnection {
    private XpNamedPipe pipe;
    
    public IndexerConnection() throws HistoryMinerException {
        try {
            pipe = XpNamedPipe.openNamedPipe("reverb-index", true);
        } catch (IOException e) {
            throw new HistoryMinerException("Failed to open pipe: " + e, e);
        }
    }
    
    public void close() {
        if (pipe != null) {
            pipe.close();
            pipe = null;
        }
    }
    
    public void indexPage(UpdatePageInfoRequest info) throws HistoryMinerException {
        sendMessage(info);
    }
    
    private void sendMessage(IndexerMessage msg) throws HistoryMinerException {
        IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(null, msg);
        ObjectMapper mapper = new ObjectMapper();
        byte [] jsonData = null;
        try {
            jsonData = mapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new HistoryMinerException("Error serializing message to JSON: " + e, e);
        }
        try {
            pipe.writeMessage(jsonData);
        } catch (IOException e) {
            throw new HistoryMinerException("Error writing data to pipe: " + e, e);
        }
    }
    
}
