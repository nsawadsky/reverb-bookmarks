package ca.ubc.cs.hminer.eclipseplugin;

import npw.NamedPipeWrapper;

public class IndexerConnection implements Runnable {
    private long pipeHandle = 0;
    
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
    
    public void sendQuery(IndexerBatchQuery query) {
        
    }
    
    @Override
    public void run() {
        
    }
    
}
