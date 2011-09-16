package ca.ubc.cs.hminer.study.core;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.hminer.indexer.messages.IndexerMessage;
import ca.ubc.cs.hminer.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.hminer.indexer.messages.PageInfo;

import npw.NamedPipeWrapper;

public class IndexerConnection {
    private long pipeHandle = 0;
    
    public IndexerConnection() throws HistoryMinerException {
        String pipeName = NamedPipeWrapper.makePipeName("historyminer-index", true);
        if (pipeName == null) {
            throw new HistoryMinerException("Failed to make pipe name: " + 
                    NamedPipeWrapper.getErrorMessage());
        }
        pipeHandle = NamedPipeWrapper.openPipe(pipeName);
        if (pipeHandle == 0) {
            throw new HistoryMinerException("Failed to open pipe: " + NamedPipeWrapper.getErrorMessage());
        }
    }
    
    public void close() {
        if (pipeHandle != 0) {
            NamedPipeWrapper.closePipe(pipeHandle);
            pipeHandle = 0;
        }
    }
    
    public void indexPage(PageInfo info) throws HistoryMinerException {
        sendMessage(info);
    }
    
    private void sendMessage(IndexerMessage msg) throws HistoryMinerException {
        IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(msg);
        ObjectMapper mapper = new ObjectMapper();
        byte [] jsonData = null;
        try {
            jsonData = mapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new HistoryMinerException("Error serializing message to JSON: " + e, e);
        }
        if (!NamedPipeWrapper.writePipe(pipeHandle, jsonData)) {
            // TODO: Close and reopen pipe?
            throw new HistoryMinerException("Error writing data to pipe: " + NamedPipeWrapper.getErrorMessage());
        }
    }
    
}
