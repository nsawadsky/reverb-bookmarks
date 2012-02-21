package ca.ubc.cs.reverb.indexer.installer;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.reverb.indexer.IndexerException;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.UpdatePageInfoRequest;

import xpnp.XpNamedPipe;

public class IndexerConnection {

    private XpNamedPipe pipe;
    
    public IndexerConnection() throws IndexerException {
        try {
            pipe = XpNamedPipe.openNamedPipe("reverb-index", true);
        } catch (IOException e) {
            throw new IndexerException("Failed to open pipe: " + e, e);
        }
    }
    
    public void close() {
        if (pipe != null) {
            pipe.close();
            pipe = null;
        }
    }
    
    public void indexPage(UpdatePageInfoRequest info) throws IndexerException {
        sendMessage(info);
    }
    
    private void sendMessage(IndexerMessage msg) throws IndexerException {
        IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(null, msg);
        ObjectMapper mapper = new ObjectMapper();
        byte [] jsonData = null;
        try {
            jsonData = mapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new IndexerException("Error serializing message to JSON: " + e, e);
        }
        try {
            pipe.writeMessage(jsonData);
        } catch (IOException e) {
            throw new IndexerException("Error writing data to pipe: " + e, e);
        }
    }
    
}
