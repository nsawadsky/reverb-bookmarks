package ca.ubc.cs.hminer.indexer;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

import npw.FailedToListenException;
import npw.NamedPipeConnection;

import org.apache.log4j.Logger;

import ca.ubc.cs.hminer.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.hminer.indexer.messages.PageInfo;

public class IndexPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(IndexPipeListener.class);

    private IndexerConfig config;
    private WebPageIndexer indexer;
    
    public IndexPipeListener(IndexerConfig config, WebPageIndexer indexer)  {
        this.config = config;
        this.indexer = indexer;
    }
    
    @Override
    public void run() {
        NamedPipeConnection listeningConn = NamedPipeConnection.newConnection("historyminer-index", true);
        
        try {
            while (true) {
                try {
                    NamedPipeConnection newConn = listeningConn.acceptConnection();
                    new Thread(new ListenerInstance(config, indexer, newConn)).start();
                } catch (IOException e) {
                    if (e instanceof FailedToListenException) {
                        throw (FailedToListenException)e;
                    }
                    log.error("Error accepting connection on index pipe", e);
                }
            }
        } catch (FailedToListenException e) {
            log.error("Error listening for connections on index pipe", e);
        }
    }
   
    private class ListenerInstance implements Runnable {
        private IndexerConfig config;
        private WebPageIndexer indexer;
        private NamedPipeConnection connection;
        
        public ListenerInstance(IndexerConfig config, WebPageIndexer indexer, NamedPipeConnection connection) {
            this.config = config;
            this.indexer = indexer;
            this.connection = connection;
        }
        
        @Override
        public void run() {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            try {
                ObjectMapper mapper = new ObjectMapper();
                while (true) {
                    byte[] data = connection.readMessage();
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
            } catch (IOException e) {
                log.error("Error reading index pipe", e);
            } finally {
                try {
                    connection.close();
                } catch (IOException e) {}
            }
        }
    }

}
