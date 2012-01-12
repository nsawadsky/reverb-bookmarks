package ca.ubc.cs.reverb.eclipseplugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
import ca.ubc.cs.reverb.indexer.messages.BatchQueryRequest;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryRequest;
import ca.ubc.cs.reverb.indexer.messages.DeleteLocationReply;
import ca.ubc.cs.reverb.indexer.messages.DeleteLocationRequest;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.IndexerReply;

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
    
    public CodeQueryReply sendCodeQueryRequest(CodeQueryRequest request, int timeoutMsecs) throws IOException, InterruptedException {
        IndexerReply reply = sendRequest(request, timeoutMsecs);
        if (! (reply instanceof CodeQueryReply)) {
            throw new IOException("Unexpected reply message type: " + reply.getClass());
        }
        return (CodeQueryReply)reply;
    }
    
    public BatchQueryReply sendBatchQueryRequest(BatchQueryRequest request, int timeoutMsecs) throws IOException, InterruptedException {
        IndexerReply reply = sendRequest(request, timeoutMsecs);
        if (! (reply instanceof BatchQueryReply)) {
            throw new IOException("Unexpected reply message type: " + reply.getClass());
        }
        return (BatchQueryReply)reply;
    }
    
    public DeleteLocationReply sendDeleteLocationRequest(DeleteLocationRequest request, int timeoutMsecs) throws IOException, InterruptedException {
        IndexerReply reply = sendRequest(request, timeoutMsecs);
        if (! (reply instanceof DeleteLocationReply)) {
            throw new IOException("Unexpected reply message type: " + reply.getClass());
        }
        return (DeleteLocationReply)reply;
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
                            callbackInfo.callback.onIndexerMessage(envelope.message, callbackInfo.clientInfo);
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
                    info.callback.onIndexerError("Error reading from pipe: " + e, e);
                } catch (Throwable t) {
                    getLogger().logError("Callback threw exception", t);
                }
            }
        }
    }
    
    public IndexerReply sendRequest(IndexerMessage msg, long timeoutMsecs) throws IOException, InterruptedException {
        class Callback implements IndexerConnectionCallback {
            IndexerMessage reply = null;
            boolean errorOccurred = false;
            String errorMessage;
            
            @Override
            public void onIndexerMessage(IndexerMessage message, Object clientInfo) {
                reply = message;
                synchronized (this) {
                    this.notify();
                }
            }
            
            @Override 
            public void onIndexerError(String message, Throwable t) {
                errorOccurred = true;
                errorMessage = message;
                synchronized(this) {
                    this.notify();
                }
            }
            
            IndexerReply waitForReply(long timeoutMsecs) throws IOException, InterruptedException {
                long expireTime = System.currentTimeMillis() + timeoutMsecs;
                synchronized (this) {
                    do {
                        this.wait(timeoutMsecs);
                        timeoutMsecs = expireTime - System.currentTimeMillis();
                    } while (reply == null && timeoutMsecs > 0);
                }
                if (errorOccurred) {
                    throw new IOException("Error while waiting for reply: " + errorMessage);
                }
                if (reply == null) {
                    throw new IOException("Timed out waiting for reply");
                }
                if (! (reply instanceof IndexerReply)) {
                    throw new IOException("Unexpected reply message type: " + reply.getClass());
                }
                return (IndexerReply)reply;
            }
            
        }
        Callback callback = new Callback();
        sendRequestAsync(msg, callback, null);
        return callback.waitForReply(timeoutMsecs);
    }
        
    
    public void sendRequestAsync(IndexerMessage msg, IndexerConnectionCallback callback, Object clientInfo) {
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
            callback.onIndexerError("Error sending request to indexer: " + e, e);
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
