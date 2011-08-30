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
            new Thread(new ListenerInstance(config, indexPipeName, pagesQueue)).start();
        }
    }
   
    private class ListenerInstance implements Runnable {
        private IndexerConfig config;
        private String indexPipeName;
        private BlockingQueue<PageInfo> pagesQueue;
        
        public ListenerInstance(IndexerConfig config, String indexPipeName, BlockingQueue<PageInfo> pagesQueue) {
            this.config = config;
            this.indexPipeName = indexPipeName;
            this.pagesQueue = pagesQueue;
        }
        
        public void run() {
            long pipeHandle = NamedPipeWrapper.createPipe(indexPipeName, true);
            if (pipeHandle == 0) {
                log.error("Failed to create pipe '" + indexPipeName + "': " + NamedPipeWrapper.getErrorMessage());
                return;
            }
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                
                ObjectMapper mapper = new ObjectMapper();
                while (true) {
                    if (!NamedPipeWrapper.connectPipe(pipeHandle)) {
                        log.error("Failed to connect index pipe: " + NamedPipeWrapper.getErrorMessage());
                        return;
                    }
                    
                    byte[] data = null;
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
            } finally {
                NamedPipeWrapper.closePipe(pipeHandle);
            }
        }
    }
}
