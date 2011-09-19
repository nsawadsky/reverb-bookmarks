package ca.ubc.cs.hminer.indexer;

import org.codehaus.jackson.map.ObjectMapper;
import npw.WindowsNamedPipe;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import ca.ubc.cs.hminer.indexer.messages.BatchQueryResult;
import ca.ubc.cs.hminer.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.hminer.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.hminer.indexer.messages.QueryResult;


public class QueryPipeListener {
    private static Logger log = Logger.getLogger(QueryPipeListener.class);

    private static final int LISTENING_THREADS = 5;
    
    private IndexerConfig config;
    private String queryPipeName;
    private WebPageIndexer indexer;
    
    public QueryPipeListener(IndexerConfig config, WebPageIndexer indexer) throws IndexerException  {
        this.config = config;
        this.indexer = indexer;
        this.queryPipeName = WindowsNamedPipe.makePipeName("historyminer-query", true);
        if (queryPipeName == null) {
            throw new IndexerException("Failed to make query pipe name: " + WindowsNamedPipe.getErrorMessage());
        }
    }
    
    public void start() throws IndexerException {
        IndexReader reader;
        try {
            // IndexReader is thread-safe, share it for efficiency.
            reader = IndexReader.open(indexer.getIndexWriter(), true);
        } catch (Exception e) {
            throw new IndexerException("Error creating IndexReader: " + e, e);
        }
        
        for (int i = 0; i < LISTENING_THREADS; i++) {
            new Thread(new ListenerInstance(config, queryPipeName, reader)).start();
        }
    }
   
    private class ListenerInstance implements Runnable {
        private long pipeHandle = 0;
        private WebPageSearcher searcher;
        private IndexerConfig config;
        private String queryPipeName;
        
        public ListenerInstance(IndexerConfig config, String queryPipeName, IndexReader reader) {
            this.config = config;
            this.queryPipeName = queryPipeName;
            searcher = new WebPageSearcher(config, reader);
        }
        
        public void run() {
            pipeHandle = WindowsNamedPipe.createPipe(queryPipeName, true);
            if (pipeHandle == 0) {
                log.error("Failed to create pipe '" + queryPipeName + "': " + WindowsNamedPipe.getErrorMessage());
                return;
            }
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                
                ObjectMapper mapper = new ObjectMapper();
                while (true) {
                    if (!WindowsNamedPipe.connectPipe(pipeHandle)) {
                        log.error("Failed to connect query pipe: " + WindowsNamedPipe.getErrorMessage());
                        return;
                    }
                    
                    byte[] data = null;
                    while ((data = WindowsNamedPipe.readPipe(pipeHandle)) != null) {
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
                    
                    log.info("Error reading query pipe: " + WindowsNamedPipe.getErrorMessage());
                    
                    if (!WindowsNamedPipe.disconnectPipe(pipeHandle)) {
                        log.error("Failed to disconnect query pipe: " + WindowsNamedPipe.getErrorMessage());
                        return;
                    }
                } 
            } finally {
                WindowsNamedPipe.closePipe(pipeHandle);
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
            if (!WindowsNamedPipe.writePipe(pipeHandle, jsonData)) {
                throw new IndexerException("Error writing data to pipe: " + WindowsNamedPipe.getErrorMessage());
            }
    
        }
    }
}
