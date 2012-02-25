package ca.ubc.cs.reverb.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import xpnp.XpNamedPipe;

import org.apache.log4j.Logger;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
import ca.ubc.cs.reverb.indexer.messages.BatchQueryRequest;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryRequest;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryResult;
import ca.ubc.cs.reverb.indexer.messages.DeleteLocationRequest;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.messages.IndexerReply;
import ca.ubc.cs.reverb.indexer.messages.LogClickRequest;
import ca.ubc.cs.reverb.indexer.messages.LogPluginViewStateRequest;
import ca.ubc.cs.reverb.indexer.messages.QueryResult;
import ca.ubc.cs.reverb.indexer.messages.UploadLogsRequest;
import ca.ubc.cs.reverb.indexer.study.RecommendationClickEvent;
import ca.ubc.cs.reverb.indexer.study.StudyDataCollector;
import ca.ubc.cs.reverb.indexer.study.PluginViewStateChangedEvent;


public class QueryPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(QueryPipeListener.class);

    private IndexerConfig config;
    private WebPageIndexer indexer;
    private XpNamedPipe listeningPipe;
    private SharedIndexReader indexReader = null;
    private LocationsDatabase locationsDatabase;
    private StudyDataCollector collector;
    
    public QueryPipeListener(IndexerConfig config, WebPageIndexer indexer, LocationsDatabase locationsDatabase,
            StudyDataCollector collector) {
        this.config = config;
        this.indexer = indexer;
        this.locationsDatabase = locationsDatabase;
        this.collector = collector;
    }
    
    public void start() throws IndexerException {
        try {
            indexReader = indexer.getNewIndexReader();
            
            try {
                listeningPipe = XpNamedPipe.createNamedPipe(config.getQueryPipeName(), true);
            } catch (IOException e) {
                throw new IndexerException("Error creating query pipe: " + e, e);
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
            try {
                XpNamedPipe newPipe = listeningPipe.acceptConnection();
                log.info("Accepted connection on query pipe");
                new Thread(new QueryPipeConnection(config, newPipe, indexReader, locationsDatabase, collector)).start();
            } catch (Throwable t) {
                log.error("Error accepting connection on query pipe", t);
            }
        }
    }
   
    private class QueryPipeConnection implements Runnable {
        private XpNamedPipe pipe;
        private WebPageSearcher searcher;
        private IndexerConfig config;
        
        public QueryPipeConnection(IndexerConfig config, XpNamedPipe pipe, SharedIndexReader reader, 
                LocationsDatabase locationsDatabase, StudyDataCollector collector) {
            this.config = config;
            this.pipe = pipe;
            searcher = new WebPageSearcher(config, reader, locationsDatabase, collector);
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
                        if (envelope.message instanceof BatchQueryRequest) {
                            handleBatchQueryRequest(envelope.clientRequestId, (BatchQueryRequest)envelope.message);
                        } else if (envelope.message instanceof DeleteLocationRequest) {
                            handleDeleteLocationRequest(envelope.clientRequestId, (DeleteLocationRequest)envelope.message);
                        } else if (envelope.message instanceof CodeQueryRequest) {
                            handleCodeQueryRequest(envelope.clientRequestId, (CodeQueryRequest)envelope.message);
                        } else if (envelope.message instanceof UploadLogsRequest) {
                            handleUploadLogsRequest(envelope.clientRequestId, (UploadLogsRequest)envelope.message);
                        } else if (envelope.message instanceof LogClickRequest) {
                            handleLogClickRequest(envelope.clientRequestId, (LogClickRequest)envelope.message);
                        } else if (envelope.message instanceof LogPluginViewStateRequest) {
                            handleLogViewStateRequest(envelope.clientRequestId, (LogPluginViewStateRequest)envelope.message);
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
        
        private void handleLogViewStateRequest(String clientRequestId, LogPluginViewStateRequest request) throws IndexerException {
            collector.logEvent(new PluginViewStateChangedEvent(System.currentTimeMillis(), request.isViewOpen));
            sendReply(clientRequestId, new IndexerReply());
        }
        
        private void handleLogClickRequest(String clientRequestId, LogClickRequest request) throws IndexerException {
            LocationInfo info = locationsDatabase.getLocationInfo(request.location.url);
            if (info != null) {
                RecommendationClickEvent event = new RecommendationClickEvent(System.currentTimeMillis(), 
                        info, request.location.frecencyBoost, request.location.relevance, 
                        request.location.overallScore, request.resultGenTimestamp);
                collector.logEvent(event);
            }
            sendReply(clientRequestId, new IndexerReply());
        }
        
        private void handleUploadLogsRequest(String clientRequestId, UploadLogsRequest request) throws IndexerException {
            IndexerReply reply = new IndexerReply();
            try {
                collector.pushDataToServer(request.isFinalUpload);
            } catch (IndexerException e) {
                reply.errorOccurred = true;
                reply.errorMessage = e.toString();
            }
            sendReply(clientRequestId, reply);
        }
        
        private void handleDeleteLocationRequest(String clientRequestId, DeleteLocationRequest request) throws IndexerException {
            IndexerReply reply = new IndexerReply();
            try {
                indexer.deleteLocation(request);
            } catch (IndexerException e) {
                reply.errorOccurred = true;
                reply.errorMessage = e.toString();
            }
            sendReply(clientRequestId, reply);
        }
        
        private void handleCodeQueryRequest(String clientRequestId, CodeQueryRequest codeQuery) throws IndexerException {
            CodeQueryReply codeQueryReply = null;
            try {
                QueryBuilder builder = new QueryBuilder(codeQuery.codeElements);
                builder.buildQueries();
                BatchQueryReply batchQueryReply = searcher.performSearch(
                        builder.getQueries());
                codeQueryReply = new CodeQueryReply(batchQueryReply.resultGenTimestamp);
                codeQueryReply.errorElements = builder.getErrorElements();
                for (QueryResult result: batchQueryReply.queryResults) {
                    List<String> allKeywords = new ArrayList<String>();
                    for (IndexerQuery indexerQuery: result.indexerQueries) {
                        String[] queryKeywords = indexerQuery.queryClientInfo.split(" ");
                        for (String keyword: queryKeywords) {
                            if (!allKeywords.contains(keyword)) {
                                allKeywords.add(keyword);
                            }
                        }
                    }
                    StringBuilder displayText = new StringBuilder();
                    for (String keyword: allKeywords) {
                        if (displayText.length() > 0) {
                            displayText.append(" ");
                        }
                        displayText.append(keyword);
                    }
                    codeQueryReply.queryResults.add(
                            new CodeQueryResult(result.locations, displayText.toString()));
                }
            } catch (IndexerException e) {
                codeQueryReply = new CodeQueryReply(true, e.toString());
            }
            sendReply(clientRequestId, codeQueryReply);
        }
        
        private void handleBatchQueryRequest(String clientRequestId, BatchQueryRequest query) throws IndexerException {
            BatchQueryReply reply = null;
            try {
                reply = searcher.performSearch(query.queries);
            } catch (IndexerException e) {
                reply = new BatchQueryReply(true, e.toString());
            }
            sendReply(clientRequestId, reply);
        }
        
        private void sendReply(String clientRequestId, IndexerReply reply) throws IndexerException {
            ObjectMapper mapper = new ObjectMapper();
            IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(clientRequestId, reply);
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
