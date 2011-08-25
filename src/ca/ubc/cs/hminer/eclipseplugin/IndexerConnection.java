package ca.ubc.cs.hminer.eclipseplugin;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.util.DefaultPrettyPrinter;

import ca.ubc.cs.hminer.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.hminer.indexer.messages.IndexerMessage;
import ca.ubc.cs.hminer.indexer.messages.BatchQueryResult;
import ca.ubc.cs.hminer.study.core.LocationAndClassification;

import npw.NamedPipeWrapper;

public class IndexerConnection implements Runnable {
    private long pipeHandle = 0;
    
    // Access to next two variables must be synchronized on callbacks reference.
    private Map<Long, CallbackInfo> callbacks = new HashMap<Long, CallbackInfo>();
    private Long nextCallbackId = 1L;
    
    private class CallbackInfo {
        CallbackInfo(IndexerConnectionCallback<?> callback, boolean multiShot ) {
            this.callback = callback;
            this.isMultiShot = multiShot;
        }
        IndexerConnectionCallback<?> callback;
        boolean isMultiShot; 
    }
    
    public IndexerConnection() throws HistoryMinerPluginException {
        String pipeName = NamedPipeWrapper.makePipeName("historyminer-query", true);
        if (pipeName == null) {
            throw new HistoryMinerPluginException("Failed to make pipe name: " + 
                    NamedPipeWrapper.getErrorMessage());
        }
        pipeHandle = NamedPipeWrapper.openPipe(pipeName);
        if (pipeHandle == 0) {
            throw new HistoryMinerPluginException("Failed to open pipe: " + NamedPipeWrapper.getErrorMessage());
        }
    }
    
    public void start() {
        new Thread(this).start();
    }
    
    public void sendQuery(IndexerBatchQuery query, IndexerConnectionCallback<BatchQueryResult> callback) {
        sendMessage(query, callback, false);
    }
    
    @Override
    public void run() {
        
    }
    
    private void sendMessage(IndexerMessage msg, IndexerConnectionCallback<?> callback, boolean multiShot) {
        Long callbackId = 0L;
        synchronized (callbacks) {
            callbackId = getNextCallbackId();
            callbacks.put(callbackId, new CallbackInfo(callback, multiShot));
        }
        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);
        JsonGenerator jsonGenerator = mapper.getJsonFactory().createJsonGenerator(writer);
        jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

        Class<?> viewClass = Object.class;
        if (historyMinerData.anonymizePartial) {
            viewClass = LocationAndClassification.AnonymizePartial.class;
        }
        ObjectWriter objWriter = mapper.viewWriter(viewClass);
        objWriter.writeValue(jsonGenerator, historyReport);
        
        return writer.toString();

        
    }
    
    private Long getNextCallbackId() {
        synchronized (callbacks) {
            return nextCallbackId++;
        }
    }
    
}
