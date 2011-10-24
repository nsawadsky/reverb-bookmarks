package ca.ubc.cs.periscope.eclipseplugin;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.periscope.indexer.messages.BatchQueryResult;
import ca.ubc.cs.periscope.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.periscope.indexer.messages.IndexerMessage;
import ca.ubc.cs.periscope.indexer.messages.IndexerMessageEnvelope;

import xpnp.XpNamedPipe;

public class IndexerConnection {
    private XpNamedPipe pipe;
    
    public IndexerConnection() throws PluginException {
        try {
            pipe = XpNamedPipe.openNamedPipe("historyminer-query", true);
        } catch (IOException e) {
            throw new PluginException("Failed to open pipe: " + e, e);
        }
    }
    
    public BatchQueryResult runQuery(IndexerBatchQuery query) throws PluginException {
        IndexerMessage response = sendMessage(query);
        if (!(response instanceof BatchQueryResult)) {
            throw new PluginException("Response not instance of BatchQueryResult");
        } 
        return (BatchQueryResult)response;
    }
    
    public void close() {
        if (pipe != null) {
            pipe.close();
            pipe = null;
        }
    }
    
    private IndexerMessage sendMessage(IndexerMessage msg) throws PluginException {
        IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(msg);
        ObjectMapper mapper = new ObjectMapper();
        byte [] jsonData = null;
        try {
            jsonData = mapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new PluginException("Error serializing message to JSON: " + e, e);
        }
        try {
            pipe.writeMessage(jsonData);
        } catch (IOException e) {
            throw new PluginException("Error writing data to pipe: " + e, e);
        }
        
        byte [] responseData = null;
        try {
            responseData = pipe.readMessage();
        } catch (IOException e) {
            // TODO: Close and reopen pipe?
            throw new PluginException("Error reading response from pipe: " + e, e);
        }
        
        try {
            return mapper.readValue(responseData, IndexerMessageEnvelope.class).message;
        } catch (Exception e) {
            throw new PluginException("Error deserializing message: " + e, e);
        }
        
    }
    
}
