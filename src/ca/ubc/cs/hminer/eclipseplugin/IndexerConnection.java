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
    
    private class CallbackInvoker<MessageClass extends IndexerMessage> {
        private IndexerConnectionCallback<MessageClass> callback;
        private Class<MessageClass> messageClass;
        
        public CallbackInvoker(IndexerConnectionCallback<MessageClass> callback, Class<MessageClass> messageClass) {
            this.callback = callback;
            this.messageClass = messageClass;
        }
        
        public void invoke(IndexerMessage msg) {
            callback.handleMessage(messageClass.cast(msg));
        }
    }
    
    private class CallbackInfo {
        public CallbackInfo(CallbackInvoker<?> callbackInvoker, boolean multiShot ) {
            this.callbackInvoker = callbackInvoker;
            this.isMultiShot = multiShot;
        }
        public CallbackInvoker<?> callbackInvoker;
        public boolean isMultiShot; 
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
    
    public void stop() {
        if (pipeHandle != 0) {
            NamedPipeWrapper.closePipe(pipeHandle);
        }
    }
    
    public void sendQuery(IndexerBatchQuery query, IndexerConnectionCallback<BatchQueryResult> callback) throws PluginException {
        sendMessage(query, new CallbackInvoker<BatchQueryResult>(callback, BatchQueryResult.class), false);
    }
    
    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTyping();
        byte[] data = null;
        while ((data = NamedPipeWrapper.readPipe(pipeHandle)) != null) {
            try {
                IndexerMessageEnvelope envelope = mapper.readValue(data, IndexerMessageEnvelope.class);
                CallbackInfo info = null;
                synchronized (callbacks) {
                    info = callbacks.get(envelope.requestId);
                    if (info != null && !info.isMultiShot) {
                        callbacks.remove(envelope.requestId);
                    }
                }
                if (info == null) {
                    getLogger().logError("No callback for request ID " + envelope.requestId);
                } else {
                    info.callbackInvoker.invoke(envelope.message);
                }
            } catch (Exception e) {
                getLogger().logError("Exception parsing message from indexer query pipe", e);
            }
        }
        
        getLogger().logInfo("Error reading indexer query pipe: " + NamedPipeWrapper.getErrorMessage(), null);
    }
    
    private void sendMessage(IndexerMessage msg, CallbackInvoker<?> callbackInvoker, boolean multiShot) throws PluginException {
        Long requestId = 0L;
        synchronized (callbacks) {
            requestId = getNextRequestId();
            callbacks.put(requestId, new CallbackInfo(callbackInvoker, multiShot));
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
            // TODO: Close and reopen pipe?
            throw new PluginException("Error writing data to pipe: " + NamedPipeWrapper.getErrorMessage());
        }
        
    }
    
    private Long getNextRequestId() {
        synchronized (callbacks) {
            return requestId++;
        }
    }
    
    private PluginLogger getLogger() {
        return PluginActivator.getDefault().getLogger();
    }
}
