package ca.ubc.cs.periscope.indexer;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import npw.NamedPipeWrapper;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import ca.ubc.cs.periscope.indexer.messages.BatchQueryResult;
import ca.ubc.cs.periscope.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.periscope.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.periscope.indexer.messages.QueryResult;


public class QueryPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(QueryPipeListener.class);

    private IndexerConfig config;
    private WebPageIndexer indexer;
    private long listeningPipe = 0;
    private IndexReader indexReader = null;
    
    public QueryPipeListener(IndexerConfig config, WebPageIndexer indexer) {
        this.config = config;
        this.indexer = indexer;
    }
    
    public void start() throws IndexerException {
        try {
            try {
                // IndexReader is thread-safe, share it for efficiency.
                indexReader = IndexReader.open(indexer.getIndexWriter(), true);
            } catch (Exception e) {
                throw new IndexerException("Error creating IndexReader: " + e, e);
            }
            
            String queryPipeName = NamedPipeWrapper.makePipeName("historyminer-query", true);
            if (queryPipeName == null) {
                throw new IndexerException("Failed to make query pipe name: " + NamedPipeWrapper.getErrorMessage());
            }
    
            listeningPipe = NamedPipeWrapper.createPipe(queryPipeName, true);
            if (listeningPipe == 0) {
                throw new IndexerException("Failed to create query pipe: " + NamedPipeWrapper.getErrorMessage());
            }

            new Thread(this).start();
        } catch (IndexerException e) {
            if (indexReader != null) {
                try {
                    indexReader.close();
                } catch (IOException ioExcept) {}
            }
            throw e;
        }
    }
    
    public void run() {
        while (true) {
            long newPipe = NamedPipeWrapper.acceptConnection(listeningPipe);
            if (newPipe == 0) {
                log.error("Error accepting connection on index pipe: " + NamedPipeWrapper.getErrorMessage());
            } else {
                new Thread(new ListenerInstance(config, newPipe, indexReader)).start();
            }
        }
    }
   
    private class ListenerInstance implements Runnable {
        private long pipeHandle = 0;
        private WebPageSearcher searcher;
        private IndexerConfig config;
        
        public ListenerInstance(IndexerConfig config, long pipeHandle, IndexReader reader) {
            this.config = config;
            this.pipeHandle = pipeHandle;
            searcher = new WebPageSearcher(config, reader);
        }
        
        public void run() {
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                
                ObjectMapper mapper = new ObjectMapper();
                byte[] data = null;
                while ((data = NamedPipeWrapper.readPipe(pipeHandle)) != null) {
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
                
                log.info("Error reading query pipe: " + NamedPipeWrapper.getErrorMessage());
            } finally {
                NamedPipeWrapper.closePipe(pipeHandle);
            }
        }
        
        private void handleBatchQuery(IndexerBatchQuery query) throws IndexerException {
            BatchQueryResult result = new BatchQueryResult();
            for (String queryString: query.queryStrings) {
                try {
                    result.queryResults.add(new QueryResult(queryString, searcher.performSearch(queryString)));
                } catch (IndexerException e) {
                    log.error("Error while processing batch query", e);
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            
            IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(result);
            byte [] jsonData = null;
            try {
                jsonData = mapper.writeValueAsBytes(envelope);
            } catch (Exception e) {
                throw new IndexerException("Error serializing message to JSON: " + e, e);
            }
            if (!NamedPipeWrapper.writePipe(pipeHandle, jsonData)) {
                throw new IndexerException("Error writing data to pipe: " + NamedPipeWrapper.getErrorMessage());
            }
    
        }
    }
}
