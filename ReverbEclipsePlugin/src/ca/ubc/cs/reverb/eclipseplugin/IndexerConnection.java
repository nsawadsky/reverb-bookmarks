package ca.ubc.cs.reverb.eclipseplugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryRequest;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.IndexerReply;
import ca.ubc.cs.reverb.indexer.messages.UploadLogsRequest;

import xpnp.XpNamedPipe;

public class IndexerConnection implements Runnable {
    /**
     * Handle may be set to null and reset to point to new pipe by reader thread.  Since this
     * handle can be accessed by separate writing threads without synchronization, must flag it as volatile.
     */
    private volatile XpNamedPipe pipe;
    
    /**
     * Access must be synchronized on the IndexerConnection object.
     */
    private Long nextRequestId = 0L;
    
    /**
     * Access must be synchronized on the callbacks reference.
     */
    private Map<Long, CallbackInfo> callbacks = new HashMap<Long, CallbackInfo>();
    
    private PluginLogger logger;
    private PluginConfig config;
    
    /**
     * Access must be synchronized on the listeners reference.
     */
    private List<IndexerConnectionListener> listeners = new ArrayList<IndexerConnectionListener>();
    
    public IndexerConnection(PluginConfig config, PluginLogger logger) {
        this.config = config;
        this.logger = logger;
    }
    
    public void start() throws IOException {
        new Thread(this).start();
    }
    
    public void addListener(IndexerConnectionListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(IndexerConnectionListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    public IndexerReply sendUploadLogsRequest(UploadLogsRequest request, int timeoutMsecs) throws IOException, InterruptedException {
        return sendRequest(request, timeoutMsecs);
    }
    
    public CodeQueryReply sendCodeQueryRequest(CodeQueryRequest request, int timeoutMsecs) throws IOException, InterruptedException {
        IndexerReply reply = sendRequest(request, timeoutMsecs);
        if (! (reply instanceof CodeQueryReply)) {
            throw new IOException("Unexpected reply message type: " + reply.getClass());
        }
        return (CodeQueryReply)reply;
    }
    
    @Override
    public void run() {
        // This thread runs at normal priority, since time-sensitive UI actions (such as
        // deleting a location) depend on it.
        ObjectMapper mapper = new ObjectMapper();
        while (true) {
            while (pipe == null) {
                pipe = connectToIndexer();
                if (pipe == null) {
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e) { }
                }
            }
            
            try {
                byte[] responseData = pipe.readMessage();
                IndexerMessageEnvelope envelope = null;
                Long requestId = 0L;
                try {
                    envelope = mapper.readValue(responseData, IndexerMessageEnvelope.class);
                    requestId = Long.parseLong(envelope.clientRequestId);
                } catch (Exception e) {
                    logger.logError("Error deserializing message from JSON", e);
                }
                if (envelope != null) {
                    CallbackInfo callbackInfo = removeCallbackInfo(requestId);
                    if (callbackInfo != null) {
                        try {
                            callbackInfo.callback.onIndexerMessage(envelope.message, callbackInfo.clientInfo);
                        } catch (Throwable t) {
                            logger.logError("Callback threw exception", t);
                        }
                    }
                }
            } catch (IOException e) {
                pipe.close();
                pipe = null;
                
                logger.logError("Error reading from pipe", e);
                for (CallbackInfo info: removeAllCallbacks()) {
                    try {
                        info.callback.onIndexerError("Error reading from pipe: " + e, e);
                    } catch (Throwable t) {
                        logger.logError("Callback threw exception", t);
                    }
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
            public synchronized void onIndexerMessage(IndexerMessage message, Object clientInfo) {
                reply = message;
                this.notify();
            }
            
            @Override 
            public synchronized void onIndexerError(String message, Throwable t) {
                errorOccurred = true;
                errorMessage = message;
                this.notify();
            }
            
            synchronized IndexerReply waitForReply(long timeoutMsecs) throws IOException, InterruptedException {
                long expireTime = System.currentTimeMillis() + timeoutMsecs;
                while (reply == null && ! errorOccurred && timeoutMsecs > 0) {
                    this.wait(timeoutMsecs);
                    timeoutMsecs = expireTime - System.currentTimeMillis();
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
        if (callback != null) {
            putCallbackInfo(requestId, new CallbackInfo(callback, clientInfo));
        }
        
        try {
            // Copy pipe handle, in case the reader thread sets it to null.
            XpNamedPipe tempPipe = pipe;

            if (tempPipe == null) {
                throw new IOException("Indexer connection is down");
            }
            
            IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(requestId.toString(), msg);

            ObjectMapper mapper = new ObjectMapper();
            byte [] jsonData = null;
            try {
                jsonData = mapper.writeValueAsBytes(envelope);
            } catch (Exception e) {
                throw new IOException("Error serializing message to JSON: " + e, e);
            }
            
            tempPipe.writeMessage(jsonData);
        } catch (IOException e) {
            removeCallbackInfo(requestId);
            if (callback != null) {
                callback.onIndexerError("Error sending request to indexer: " + e, e);
            }
        }
    }
    
    private void notifyListenersConnectionEstablished() {
        List<IndexerConnectionListener> copy = null;
        synchronized (listeners) {
            copy = new ArrayList<IndexerConnectionListener>(listeners);
        }
        for (IndexerConnectionListener listener: copy) {
            try {
                listener.onIndexerConnectionEstablished();
            } catch (Exception e) { }
        }
    }
    
    private XpNamedPipe connectToIndexer() {
        XpNamedPipe result = null;
        try {
            result = XpNamedPipe.openNamedPipe("reverb-query", true);
        } catch (IOException e) { }
        if (result == null) {
            try {
                String indexerInstallPath = config.getCurrentIndexerInstallPath();
                File indexerJarPath = new File(indexerInstallPath + File.separator + "ReverbIndexer.jar");
                if (indexerJarPath.exists()) {
                    XpNamedPipe.startProcess(
                            "javaw.exe -Djava.library.path=native -Xmx1024m -jar ReverbIndexer.jar", 
                            indexerInstallPath);
                    int tries = 0;
                    while (result == null && tries++ < 10) {
                        try { 
                            Thread.sleep(500);
                            result = XpNamedPipe.openNamedPipe("reverb-query", true);
                        } catch (Exception e) { }
                    }
                }
            } catch (Exception e) { }
        }
        if (result != null) {
            notifyListenersConnectionEstablished();
        }
        return result;
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
    
    private class CallbackInfo {
        IndexerConnectionCallback callback;
        Object clientInfo;
        
        CallbackInfo(IndexerConnectionCallback callback, Object clientInfo) {
            this.callback = callback;
            this.clientInfo = clientInfo;
        }
    }
    
}
