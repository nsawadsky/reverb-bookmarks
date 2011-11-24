package ca.ubc.cs.reverb.indexer;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import xpnp.XpNamedPipe;

import org.apache.log4j.Logger;

import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.UpdatePageInfoRequest;


public class IndexPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(IndexPipeListener.class);

    private IndexerConfig config;
    private WebPageIndexer indexer;
    private XpNamedPipe listeningPipe;
    
    public IndexPipeListener(IndexerConfig config, WebPageIndexer indexer) {
        this.config = config;
        this.indexer = indexer;
    }
    
    public void start() throws IndexerException {
        try {
            listeningPipe = XpNamedPipe.createNamedPipe(config.getIndexerPipeName(), true);
        } catch (IOException e) {
            throw new IndexerException("Failed to create index pipe: " + e, e);
        }
        new Thread(this).start();
    }
    
    public void run() {
        while (true) {
            try {
                XpNamedPipe newPipe = listeningPipe.acceptConnection();
                log.info("Accepted connection on index pipe");
                new Thread(new IndexPipeConnection(config, newPipe, indexer)).start();
            } catch (IOException e) {
                log.error("Error accepting connection on query pipe", e);
            }
        }
    }
   
    private class IndexPipeConnection implements Runnable {
        private IndexerConfig config;
        private XpNamedPipe pipe;
        private WebPageIndexer indexer;
        
        public IndexPipeConnection(IndexerConfig config, XpNamedPipe pipe, WebPageIndexer indexer) {
            this.config = config;
            this.pipe = pipe;
            this.indexer = indexer;
        }
        
        public void run() {
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                
                ObjectMapper mapper = new ObjectMapper();
                while (true) {
                    byte[] data = pipe.readMessage();
                    UpdatePageInfoRequest info = null;
                    try {
                        IndexerMessageEnvelope envelope = mapper.readValue(data, IndexerMessageEnvelope.class);
                        if (envelope.message == null) {
                            throw new IndexerException("envelope.message is null");
                        }
                        if (!(envelope.message instanceof UpdatePageInfoRequest)) {
                            throw new IndexerException("Unexpected message content: " + envelope.message.getClass());
                        }
                        info = (UpdatePageInfoRequest)envelope.message;
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
            } catch (IOException e) {
                log.info("Error reading index pipe", e);
            } finally {
                pipe.close();
            }
        }
    }
}
