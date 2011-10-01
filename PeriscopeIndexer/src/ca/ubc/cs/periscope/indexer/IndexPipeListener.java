package ca.ubc.cs.periscope.indexer;

import org.codehaus.jackson.map.ObjectMapper;
import npw.NamedPipeWrapper;

import org.apache.log4j.Logger;

import ca.ubc.cs.periscope.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.periscope.indexer.messages.PageInfo;


public class IndexPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(IndexPipeListener.class);

    private IndexerConfig config;
    private WebPageIndexer indexer;
    private long listeningPipe = 0;
    
    public IndexPipeListener(IndexerConfig config, WebPageIndexer indexer) {
        this.config = config;
        this.indexer = indexer;
    }
    
    public void start() throws IndexerException {
        String indexPipeName = NamedPipeWrapper.makePipeName("historyminer-index", true); 
        if (indexPipeName == null) {
            throw new IndexerException("Failed to make index pipe name: " + NamedPipeWrapper.getErrorMessage());
        }

        listeningPipe = NamedPipeWrapper.createPipe(indexPipeName, true);
        if (listeningPipe == 0) {
            throw new IndexerException("Failed to create index pipe: " + NamedPipeWrapper.getErrorMessage());
        }
        new Thread(this).start();
    }
    
    public void run() {
        while (true) {
            long newPipe = NamedPipeWrapper.acceptConnection(listeningPipe);
            if (newPipe == 0) {
                log.error("Error accepting connection on index pipe: " + NamedPipeWrapper.getErrorMessage());
            } else {
                new Thread(new ListenerInstance(config, newPipe, indexer)).start();
            }
        }
    }
   
    private class ListenerInstance implements Runnable {
        private IndexerConfig config;
        private long pipeHandle;
        private WebPageIndexer indexer;
        
        public ListenerInstance(IndexerConfig config, long pipeHandle, WebPageIndexer indexer) {
            this.config = config;
            this.pipeHandle = pipeHandle;
            this.indexer = indexer;
        }
        
        public void run() {
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                
                ObjectMapper mapper = new ObjectMapper();
                byte[] data = null;
                while ((data = NamedPipeWrapper.readPipe(pipeHandle)) != null) {
                    PageInfo info = null;
                    try {
                        IndexerMessageEnvelope envelope = mapper.readValue(data, IndexerMessageEnvelope.class);
                        if (envelope.message == null) {
                            throw new IndexerException("envelope.message is null");
                        }
                        if (!(envelope.message instanceof PageInfo)) {
                            throw new IndexerException("Unexpected message content: " + envelope.message.getClass());
                        }
                        info = (PageInfo)envelope.message;
                    } catch (Exception e) {
                        log.error("Exception parsing message from index pipe", e);
                    }
                    if (info != null) {
                        log.info("Got page: " + info.url);
                        try {
                            indexer.indexPage(info);
                        } catch (Exception e) {
                            log.error("Error indexing page '" + info.url + "'", e);
                        }
                    }
                }
                
                log.info("Error reading index pipe: " + NamedPipeWrapper.getErrorMessage());
            } finally {
                NamedPipeWrapper.closePipe(pipeHandle);
            }
        }
    }
}
