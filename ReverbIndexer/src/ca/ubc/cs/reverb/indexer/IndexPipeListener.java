package ca.ubc.cs.reverb.indexer;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

import xpnp.XpNamedPipe;

import org.apache.log4j.Logger;

import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.ShutdownRequest;
import ca.ubc.cs.reverb.indexer.messages.UpdatePageInfoRequest;

public class IndexPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(IndexPipeListener.class);

    private IndexerService indexerService;
    private IndexerConfig config;
    private WebPageIndexer indexer;
    private XpNamedPipe listeningPipe;
    
    public IndexPipeListener(IndexerService indexerService, IndexerConfig config, WebPageIndexer indexer) {
        this.indexerService = indexerService;
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
            } catch (Throwable t) {
                log.error("Error accepting connection on query pipe", t);
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
                    try {
                        IndexerMessageEnvelope envelope = mapper.readValue(data, IndexerMessageEnvelope.class);
                        if (envelope.message == null) {
                            throw new IndexerException("envelope.message is null");
                        }
                        if (envelope.message instanceof ShutdownRequest) {
                            indexerService.shutdown();
                            System.exit(0);
                        } else if (envelope.message instanceof UpdatePageInfoRequest) {
                            handleUpdatePageInfoRequest((UpdatePageInfoRequest)envelope.message);
                        } else {
                            throw new IndexerException("Unexpected message content: " + envelope.message.getClass());
                        }
                    } catch (Exception e) {
                        log.error("Exception parsing message from index pipe", e);
                    }
                }
            } catch (IOException e) {
                log.info("Error reading index pipe", e);
            } finally {
                pipe.close();
            }
        }
    }
    
    private void handleUpdatePageInfoRequest(UpdatePageInfoRequest info) {
        if (info.html == null || info.html.isEmpty()) {
            log.debug("Got page with empty html: " + info.url);
        } else {
            log.debug("Got page: " + info.url);
        }
        try {
            indexer.indexPage(info);
        } catch (IndexerException e) {
            log.error("Error indexing page '" + info.url + "'", e);
        }
    }
}
