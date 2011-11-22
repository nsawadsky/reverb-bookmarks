package ca.ubc.cs.reverb.indexer;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import xpnp.XpNamedPipe;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;
import ca.ubc.cs.reverb.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;


public class QueryPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(QueryPipeListener.class);

    private IndexerConfig config;
    private WebPageIndexer indexer;
    private XpNamedPipe listeningPipe;
    private SharedIndexReader indexReader = null;
    private LocationsDatabase locationsDatabase;
    
    public QueryPipeListener(IndexerConfig config, WebPageIndexer indexer, LocationsDatabase locationsDatabase) {
        this.config = config;
        this.indexer = indexer;
        this.locationsDatabase = locationsDatabase;
    }
    
    public void start() throws IndexerException {
        try {
            try {
                // IndexReader is thread-safe, share it for efficiency.
                indexReader = new SharedIndexReader(IndexReader.open(indexer.getIndexWriter(), true));
            } catch (Exception e) {
                throw new IndexerException("Error creating IndexReader: " + e, e);
            }
            
            try {
                listeningPipe = XpNamedPipe.createNamedPipe(config.getQueryPipeName(), true);
            } catch (IOException e) {
                throw new IndexerException("Error creating query pipe: " + e, e);
            }

            new Thread(this).start();
        } catch (IndexerException e) {
            if (indexReader != null) {
                try {
                    indexReader.get().close();
                } catch (IOException ioExcept) {}
            }
            throw e;
        }
    }
    
    public void run() {
        while (true) {
            try {
                XpNamedPipe newPipe = listeningPipe.acceptConnection();
                log.info("Accepted connection on query pipe");
                new Thread(new QueryPipeConnection(config, newPipe, indexReader, locationsDatabase)).start();
            } catch (IOException e) {
                log.error("Error accepting connection on query pipe", e);
            }
        }
    }
   
    private class QueryPipeConnection implements Runnable {
        private XpNamedPipe pipe;
        private WebPageSearcher searcher;
        private IndexerConfig config;
        
        public QueryPipeConnection(IndexerConfig config, XpNamedPipe pipe, SharedIndexReader reader, LocationsDatabase locationsDatabase) {
            this.config = config;
            this.pipe = pipe;
            searcher = new WebPageSearcher(config, reader, locationsDatabase);
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
                        if (envelope.message instanceof IndexerBatchQuery) {
                            handleBatchQuery(envelope.clientRequestId, (IndexerBatchQuery)envelope.message);
                        } else {
                            throw new IndexerException("Unexpected message content: " + envelope.message.getClass());
                        }
                        
                    } catch (Exception e) {
                        log.error("Exception handling message from query pipe", e);
                    }
                }
            } catch (IOException e) {
                log.info("Error reading query pipe", e);
            } finally {
                pipe.close();
            }
        }
        
        private void handleBatchQuery(String clientRequestId, IndexerBatchQuery query) throws IndexerException {
            BatchQueryResult result = searcher.performSearch(query.queries);
            ObjectMapper mapper = new ObjectMapper();
            
            IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(clientRequestId, result);
            byte [] jsonData = null;
            try {
                jsonData = mapper.writeValueAsBytes(envelope);
            } catch (Exception e) {
                throw new IndexerException("Error serializing message to JSON: " + e, e);
            }
            try {
                pipe.writeMessage(jsonData);
            } catch (IOException e) {
                throw new IndexerException("Error writing data to pipe: " + e, e);
            }
        }
    }
}