package ca.ubc.cs.hminer.eclipseplugin;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.hminer.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.hminer.indexer.messages.IndexerMessage;
import ca.ubc.cs.hminer.indexer.messages.BatchQueryResult;
import ca.ubc.cs.hminer.indexer.messages.IndexerMessageEnvelope;

import npw.NamedPipeWrapper;

public class IndexerConnection implements Runnable {
    private long pipeHandle = 0;
    
    // Access to next two variables must be synchronized on callbacks reference.
    private Map<Long, CallbackInfo> callbacks = new HashMap<Long, CallbackInfo>();
    private Long requestId = 1L;
    
    private class CallbackInfo {
        CallbackInfo(IndexerConnectionCallback<?> callback, Class<?> callbackMessageClass, boolean multiShot ) {
            this.callback = callback;
            this.isMultiShot = multiShot;
            this.callbackMessageClass = callbackMessageClass;
        }
        IndexerConnectionCallback<?> callback;
        boolean isMultiShot; 
        Class<?> callbackMessageClass;
    }
    
    public IndexerConnection() throws PluginException {
        String pipeName = NamedPipeWrapper.makePipeName("historyminer-query", true);
        if (pipeName == null) {
            throw new PluginException("Failed to make pipe name: " + 
                    NamedPipeWrapper.getErrorMessage());
        }
        pipeHandle = NamedPipeWrapper.openPipe(pipeName);
        if (pipeHandle == 0) {
            throw new PluginException("Failed to open pipe: " + NamedPipeWrapper.getErrorMessage());
        }
    }
    
    public void start() {
        new Thread(this).start();
    }
    
    public void sendQuery(IndexerBatchQuery query, IndexerConnectionCallback<BatchQueryResult> callback) throws PluginException {
        sendMessage(query, callback, BatchQueryResult.class, false);
    }
    
    @Override
    public void run() {
        
    }
    
    private void sendMessage(IndexerMessage msg, IndexerConnectionCallback<?> callback, Class<?> callbackMessageClass, boolean multiShot) throws PluginException {
        Long requestId = 0L;
        synchronized (callbacks) {
            requestId = getNextRequestId();
            callbacks.put(requestId, new CallbackInfo(callback, callbackMessageClass, multiShot));
        }
        IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(requestId, msg);
        ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTyping();
        byte [] jsonData = null;
        try {
            jsonData = mapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new PluginException("Error serializing message to JSON: " + e, e);
        }
        if (!NamedPipeWrapper.writePipe(pipeHandle, jsonData)) {
            // TODO: Reopen pipe?
            throw new PluginException("Error writing data to pipe: " + NamedPipeWrapper.getErrorMessage());
        }
        
    }
    
    private Long getNextRequestId() {
        synchronized (callbacks) {
            return requestId++;
        }
    }
    
}
