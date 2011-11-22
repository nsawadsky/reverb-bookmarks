package ca.ubc.cs.reverb.eclipseplugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;
import ca.ubc.cs.reverb.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;

import xpnp.XpNamedPipe;

public class IndexerConnection implements Runnable {
    private XpNamedPipe pipe;
    
    /**
     * Access must be synchronized on the IndexerConnection object.
     */
    private Long nextRequestId = 0L;
    
    /**
     * Access must be synchronized on the callbacks reference.
     */
    private Map<Long, CallbackInfo> callbacks = new HashMap<Long, CallbackInfo>();
    
    public IndexerConnection() throws IOException {
        pipe = XpNamedPipe.openNamedPipe("reverb-query", true);
    }
    
    public void start() {
        new Thread(this).start();
    }
    
    public void stop() throws IOException {
        pipe.stop();
    }
    
    public BatchQueryResult runQuery(IndexerBatchQuery query, int timeoutMsecs) throws IOException, InterruptedException {
        IndexerMessage result = sendMessage(query, timeoutMsecs);
        if (! (result instanceof BatchQueryResult)) {
            throw new IOException("Unexpected reply message type: " + result.getClass());
        }
        return (BatchQueryResult)sendMessage(query, timeoutMsecs);
    }
    
    @Override
    public void run() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            while (true) {
                // Exceptions from this call fall through to the outer catch.
                byte[] responseData = pipe.readMessage();
                IndexerMessageEnvelope envelope = null;
                Long requestId = 0L;
                try {
                    envelope = mapper.readValue(responseData, IndexerMessageEnvelope.class);
                    requestId = Long.parseLong(envelope.clientRequestId);
                } catch (Exception e) {
                    getLogger().logError("Error deserializing message from JSON", e);
                }
                if (envelope != null) {
                    CallbackInfo callbackInfo = removeCallbackInfo(requestId);
                    if (callbackInfo == null) {
                        getLogger().logInfo("Callback not found for request ID " + requestId);
                    } else {
                        try {
                            callbackInfo.callback.handleMessage(envelope.message, callbackInfo.clientInfo);
                        } catch (Throwable t) {
                            getLogger().logError("Callback threw exception", t);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // TODO: Close and reopen pipe?  
            getLogger().logError("Error reading from pipe", e);
            for (CallbackInfo info: removeAllCallbacks()) {
                try {
                    info.callback.handleError("Error reading from pipe: " + e, e);
                } catch (Throwable t) {
                    getLogger().logError("Callback threw exception", t);
                }
            }
        }
    }
    
    private IndexerMessage sendMessage(IndexerMessage msg, long timeoutMsecs) throws IOException, InterruptedException {
        class Callback implements IndexerConnectionCallback {
            IndexerMessage result = null;
            boolean errorOccurred = false;
            String errorMessage;
            
            @Override
            public void handleMessage(IndexerMessage message, Object clientInfo) {
                result = message;
                synchronized (this) {
                    this.notify();
                }
            }
            
            @Override 
            public void handleError(String message, Throwable t) {
                errorOccurred = true;
                errorMessage = message;
                synchronized(this) {
                    this.notify();
                }
            }
            
            IndexerMessage waitForReply(long timeoutMsecs) throws IOException, InterruptedException {
                long expireTime = System.currentTimeMillis() + timeoutMsecs;
                synchronized (this) {
                    do {
                        this.wait(timeoutMsecs);
                        timeoutMsecs = expireTime - System.currentTimeMillis();
                    } while (result == null && timeoutMsecs > 0);
                }
                if (errorOccurred) {
                    throw new IOException("Error while waiting for reply: " + errorMessage);
                }
                if (result == null) {
                    throw new IOException("Timed out waiting for reply");
                }
                return result;
            }
            
        }
        Callback callback = new Callback();
        sendMessageAsync(msg, callback, null);
        return callback.waitForReply(timeoutMsecs);
    }
        
    
    private void sendMessageAsync(IndexerMessage msg, IndexerConnectionCallback callback, Object clientInfo) throws IOException {
        Long requestId = getNextRequestId();
        putCallbackInfo(requestId, new CallbackInfo(callback, clientInfo));
        
        try {
            IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(requestId.toString(), msg);

            ObjectMapper mapper = new ObjectMapper();
            byte [] jsonData = null;
            try {
                jsonData = mapper.writeValueAsBytes(envelope);
            } catch (Exception e) {
                throw new IOException("Error serializing message to JSON: " + e, e);
            }
            
            pipe.writeMessage(jsonData);
        } catch (IOException e) {
            removeCallbackInfo(requestId);
            throw e;
        }
    }
    
    private void putCallbackInfo(Long requestId, CallbackInfo info) {
        synchronized(callbacks) {
            callbacks.put(requestId, info);
        }
    }
    
    private CallbackInfo removeCallbackInfo(Long requestId) {
        synchronized (callbacks) {
            return callbacks.remove(requestId);
        }
    }
    
    private List<CallbackInfo> removeAllCallbacks() {
        List<CallbackInfo> removed = new ArrayList<CallbackInfo>();
        synchronized (callbacks) { 
            for (CallbackInfo info: callbacks.values()) {
                removed.add(info);
            }
            callbacks.clear();
        }
        return removed;
    }
    
    private synchronized Long getNextRequestId() {
        return nextRequestId++;
    }
    
    private PluginLogger getLogger() {
        return PluginActivator.getDefault().getLogger();
    }

    private class CallbackInfo {
        IndexerConnectionCallback callback;
        Object clientInfo;
        
        CallbackInfo(IndexerConnectionCallback callback, Object clientInfo) {
            this.callback = callback;
            this.clientInfo = clientInfo;
        }
    }
    
}
