package ca.ubc.cs.hminer.indexer;

import java.util.concurrent.BlockingQueue;
import org.codehaus.jackson.map.ObjectMapper;
import npw.NamedPipeWrapper;

import org.apache.log4j.Logger;

import ca.ubc.cs.hminer.indexer.messages.PageInfo;


public class IndexPipeListener {
    private static Logger log = Logger.getLogger(IndexPipeListener.class);

    private static final int LISTENING_THREADS = 5;
    
    private IndexerConfig config;
    private BlockingQueue<PageInfo> pagesQueue;
    private String indexPipeName;
    
    public IndexPipeListener(IndexerConfig config, BlockingQueue<PageInfo> pagesQueue) throws IndexerException  {
        this.config = config;
        this.pagesQueue = pagesQueue;
        this.indexPipeName = NamedPipeWrapper.makePipeName("historyminer-index", true);
        if (indexPipeName == null) {
            throw new IndexerException("Failed to make index pipe name: " + NamedPipeWrapper.getErrorMessage());
        }
    }
    
    public void start() {
        for (int i = 0; i < LISTENING_THREADS; i++) {
            new Thread(new ListenerInstance()).start();
        }
    }
   
    private class ListenerInstance implements Runnable {
        public void run() {
            long pipeHandle = NamedPipeWrapper.createPipe(indexPipeName, true);
            if (pipeHandle == 0) {
                log.error("Failed to create pipe '" + indexPipeName + "': " + NamedPipeWrapper.getErrorMessage());
                return;
            }
            
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            
            while (true) {
                if (!NamedPipeWrapper.connectPipe(pipeHandle)) {
                    log.error("Failed to connect index pipe: " + NamedPipeWrapper.getErrorMessage());
                    return;
                }
                
                byte[] data = null;
                ObjectMapper mapper = new ObjectMapper();
                while ((data = NamedPipeWrapper.readPipe(pipeHandle)) != null) {
                    try {
                        PageInfo info = mapper.readValue(data, PageInfo.class);
                        log.info("Got page: " + info.url);
                        pagesQueue.put(info);
                    } catch (Exception e) {
                        log.error("Exception parsing message from index pipe", e);
                    }
                }
                
                log.info("Error reading index pipe: " + NamedPipeWrapper.getErrorMessage());
                
                if (!NamedPipeWrapper.disconnectPipe(pipeHandle)) {
                    log.error("Failed to disconnect index pipe: " + NamedPipeWrapper.getErrorMessage());
                    return;
                }
            }
        }
    }
}
