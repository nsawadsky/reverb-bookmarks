package ca.ubc.cs.periscope.eclipseplugin;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.periscope.indexer.messages.BatchQueryResult;
import ca.ubc.cs.periscope.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.periscope.indexer.messages.IndexerMessage;
import ca.ubc.cs.periscope.indexer.messages.IndexerMessageEnvelope;

import xpnp.XpNamedPipe;

public class IndexerConnection {
    private long pipeHandle = 0;
    
    public IndexerConnection() throws PluginException {
        String pipeName = XpNamedPipe.makePipeName("historyminer-query", true);
        if (pipeName == null) {
            throw new PluginException("Failed to make pipe name: " + 
                    XpNamedPipe.getErrorMessage());
        }
        pipeHandle = XpNamedPipe.openPipe(pipeName);
        if (pipeHandle == 0) {
            throw new PluginException("Failed to open pipe: " + XpNamedPipe.getErrorMessage());
        }
    }
    
    public void close() {
        if (pipeHandle != 0) {
            XpNamedPipe.closePipe(pipeHandle);
            pipeHandle = 0;
        }
    }
    
    public BatchQueryResult runQuery(IndexerBatchQuery query) throws PluginException {
        IndexerMessage response = sendMessage(query);
        if (!(response instanceof BatchQueryResult)) {
            throw new PluginException("Response not instance of BatchQueryResult");
        } 
        return (BatchQueryResult)response;
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
        if (!XpNamedPipe.writePipe(pipeHandle, jsonData)) {
            // TODO: Close and reopen pipe?
            throw new PluginException("Error writing data to pipe: " + XpNamedPipe.getErrorMessage());
        }
        
        byte[] responseData = XpNamedPipe.readPipe(pipeHandle);
        if (responseData == null) {
            // TODO: Close and reopen pipe?
            throw new PluginException("Error reading response from pipe: " + XpNamedPipe.getErrorMessage());
        } else {
            try {
                return mapper.readValue(responseData, IndexerMessageEnvelope.class).message;
            } catch (Exception e) {
                throw new PluginException("Error deserializing message: " + e, e);
            }
        }
        
    }
    
}
