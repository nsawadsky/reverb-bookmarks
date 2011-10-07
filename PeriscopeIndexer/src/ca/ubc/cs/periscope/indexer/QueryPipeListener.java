package ca.ubc.cs.periscope.indexer;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import xpnp.XpNamedPipe;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import ca.ubc.cs.periscope.indexer.messages.BatchQueryResult;
import ca.ubc.cs.periscope.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.periscope.indexer.messages.IndexerMessageEnvelope;


public class QueryPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(QueryPipeListener.class);

    private IndexerConfig config;
    private WebPageIndexer indexer;
    private long listeningPipe = 0;
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
            
            String queryPipeName = XpNamedPipe.makePipeName("historyminer-query", true);
            if (queryPipeName == null) {
                throw new IndexerException("Failed to make query pipe name: " + XpNamedPipe.getErrorMessage());
            }
    
            listeningPipe = XpNamedPipe.createPipe(queryPipeName, true);
            if (listeningPipe == 0) {
                throw new IndexerException("Failed to create query pipe: " + XpNamedPipe.getErrorMessage());
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
            long newPipe = XpNamedPipe.acceptConnection(listeningPipe);
            if (newPipe == 0) {
                log.error("Error accepting connection on index pipe: " + XpNamedPipe.getErrorMessage());
            } else {
                log.info("Accepted connection on query pipe");
                new Thread(new QueryPipeConnection(config, newPipe, indexReader, locationsDatabase)).start();
            }
        }
    }
   
    private class QueryPipeConnection implements Runnable {
        private long pipeHandle = 0;
        private WebPageSearcher searcher;
        private IndexerConfig config;
        
        public QueryPipeConnection(IndexerConfig config, long pipeHandle, SharedIndexReader reader, LocationsDatabase locationsDatabase) {
            this.config = config;
            this.pipeHandle = pipeHandle;
            searcher = new WebPageSearcher(config, reader, locationsDatabase);
        }
        
        public void run() {
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                
                ObjectMapper mapper = new ObjectMapper();
                byte[] data = null;
                while ((data = XpNamedPipe.readPipe(pipeHandle)) != null) {
                    try {
                        IndexerMessageEnvelope envelope = mapper.readValue(data, IndexerMessageEnvelope.class);
                        if (envelope.message == null) {
                            throw new IndexerException("envelope.message is null");
                        }
                        if (envelope.message instanceof IndexerBatchQuery) {
                            handleBatchQuery((IndexerBatchQuery)envelope.message);
                        } else {
                            throw new IndexerException("Unexpected message content: " + envelope.message.getClass());
                        }
                        
                    } catch (Exception e) {
                        log.error("Exception handling message from query pipe", e);
                    }
                }
                
                log.info("Error reading query pipe: " + XpNamedPipe.getErrorMessage());
            } finally {
                XpNamedPipe.closePipe(pipeHandle);
            }
        }
        
        private void handleBatchQuery(IndexerBatchQuery query) throws IndexerException {
            BatchQueryResult result = searcher.performSearch(query.queryStrings);
            ObjectMapper mapper = new ObjectMapper();
            
            IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(result);
            byte [] jsonData = null;
            try {
                jsonData = mapper.writeValueAsBytes(envelope);
            } catch (Exception e) {
                throw new IndexerException("Error serializing message to JSON: " + e, e);
            }
            if (!XpNamedPipe.writePipe(pipeHandle, jsonData)) {
                throw new IndexerException("Error writing data to pipe: " + XpNamedPipe.getErrorMessage());
            }
    
        }
    }
}
