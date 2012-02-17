package ca.ubc.cs.reverb.indexer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.spi.RootLogger;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.Similarity;
import org.codehaus.jackson.map.ObjectMapper;

import xpnp.XpNamedPipe;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.QueryResult;
import ca.ubc.cs.reverb.indexer.messages.ShutdownRequest;
import ca.ubc.cs.reverb.indexer.study.StudyDataCollector;

public class IndexerService {
    private static Logger log = Logger.getLogger(IndexerService.class);

    private IndexerConfig config;
    private LocationsDatabase locationsDatabase;
    private WebPageIndexer indexer;
    
    public static void main(String[] args) {
        IndexerService service = new IndexerService();
        service.start(args);
    }

    public void start(String[] args) {
        configureConsoleDebugLogging();

        try {
            config = new IndexerConfig();

            IndexerArgsInfo argsInfo = parseArgs(args);
            
            if (argsInfo.mode == IndexerMode.UNINSTALL) {
                unregisterAndShutdownService();
                log.info("Unregistered and shutdown service successfully");
                return;
            }

            if (argsInfo.mode == IndexerMode.INSTALL){
                installService();
                return;
            }
            
            // We need config to be initialized before we can configure file logging.
            // Also, for certain startup modes, we do not want to compete with the main service
            // for access to the log file.
            configureFileDebugLogging();
            
            configLucene();
            
            StudyDataCollector collector = new StudyDataCollector(config);
            collector.start();
            
            locationsDatabase = new LocationsDatabase(config);
            indexer = new WebPageIndexer(config, locationsDatabase, collector);
            
            if (argsInfo.mode == IndexerMode.QUERY) {
                runQuery(config, indexer, locationsDatabase, collector, argsInfo.query);
                return;
            }
            
            indexer.startCommitter();
            
            IndexPipeListener indexPipeListener = new IndexPipeListener(this, config, indexer);
            
            QueryPipeListener queryPipeListener = new QueryPipeListener(config, indexer, locationsDatabase, collector);
            
            indexPipeListener.start();
            queryPipeListener.start();
            
            Runtime runtime = Runtime.getRuntime();
            log.info("Indexer service started, user ID = " + config.getUserId() + ", key = " + config.getUserIdKey() + ", max memory = " + runtime.maxMemory()/(1024*1024) + " MB");
        } catch (Throwable t) {
            log.error("Exception invoking service", t);
            System.exit(1);
        }
    }
    
    /**
     * For now, our shutdown method is minimal.  Just make sure changes are committed to the database and
     * the index.
     */
    public void shutdown() {
        try {
            indexer.shutdown();
        } catch (IndexerException e) {
            log.error("Error shutting down web page indexer", e);
        }
        try {
            locationsDatabase.close();
        } catch (IndexerException e) {
            log.error("Error closing locations database", e);
        }
    }

    private void configureConsoleDebugLogging() {
        BasicConfigurator.configure();
        RootLogger.getRootLogger().setLevel(Level.INFO);
    }
    
    private void configureFileDebugLogging() throws IOException {
        RollingFileAppender fileAppender = new RollingFileAppender(new PatternLayout("%r [%t] %p %c %x - %m%n"),
                config.getDebugLogFilePath());
        
        fileAppender.setMaxBackupIndex(2);
        fileAppender.setMaxFileSize("2MB");
        
        RootLogger.getRootLogger().addAppender(fileAppender);
    }
    
    private void runQuery(IndexerConfig config, WebPageIndexer indexer, LocationsDatabase locationsDatabase, 
            StudyDataCollector collector, String query) throws IndexerException {
        SharedIndexReader indexReader = indexer.getNewIndexReader();

        WebPageSearcher searcher = new WebPageSearcher(config, indexReader, locationsDatabase, new StudyDataCollector(config));
        
        List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
        queries.add(new IndexerQuery(query, query));
        BatchQueryReply batchResult = searcher.performSearch(queries);
        if (batchResult.queryResults.isEmpty()) {
            System.out.println("No results.");
        } else {
            System.out.println("Result list:");
            QueryResult result = batchResult.queryResults.get(0);
            for (Location loc: result.locations) {
                String display = String.format("%s (%.1f,%.1f,%.1f)", loc.title, loc.luceneScore, loc.frecencyBoost, loc.overallScore);
                System.out.println(display);
                System.out.println("    " + loc.url);
            }
        }
        
    }
    
    private void configLucene() {
        Similarity.setDefault(new DefaultSimilarity() {
            // Implementation based on Lucene 3.3.0 source code.
            @Override
            public float computeNorm(String field, FieldInvertState state) {
                final int numTerms;
                if (discountOverlaps)
                    numTerms = state.getLength() - state.getNumOverlap();
                else
                    numTerms = state.getLength();
                // Disable the length norm.
                return state.getBoost();
                //return state.getBoost() * ((float) (1.0 / Math.sqrt(numTerms)));
              }
            
            // Disable coord weighting.
            @Override 
            public float coord(int overlap, int maxOverlap) {
                return 1.0F;
            }

        });
        
    }
    
    private void installService() throws IndexerException {
        try { 
            unregisterAndShutdownService();
        } catch (IndexerException e) { }
        
        
        
    }
    
    private void unregisterAndShutdownService() throws IndexerException {
        File installPointer = new File(config.getIndexerInstallPointerPath());
        if (installPointer.exists()) {
            if (!installPointer.delete()) {
                throw new IndexerException("Failed to delete indexer install pointer file");
            }
        }
        XpNamedPipe pipe = null;
        try {
            pipe = XpNamedPipe.openNamedPipe("reverb-index", true);
            IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(null, new ShutdownRequest());

            ObjectMapper mapper = new ObjectMapper();
            byte [] jsonData = mapper.writeValueAsBytes(envelope);
            
            pipe.writeMessage(jsonData);
        } catch (Exception e) {
            throw new IndexerException("Error sending shutdown request to indexer service: " + e, e);
        } finally {
            if (pipe != null) {
                pipe.close();
            }
        }
        
    }
    
    private IndexerArgsInfo parseArgs(String[] args) {
        Map<String, String> argsMap = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-")) {
                arg = arg.substring(1);
                String value = null;
                if (i + 1 < args.length && !args[i+1].startsWith("-")) {
                    value = args[i+1];
                    i++;
                }
                argsMap.put(arg, value);
            }
        }
        if (argsMap.containsKey("query")) {
            IndexerArgsInfo result = new IndexerArgsInfo(IndexerMode.QUERY);
            result.query = argsMap.get("query");
            return result;
        }
        if (argsMap.containsKey("install")) {
            return new IndexerArgsInfo(IndexerMode.INSTALL);
        }
        if (argsMap.containsKey("uninstall")) {
            return new IndexerArgsInfo(IndexerMode.UNINSTALL);
        }
        return new IndexerArgsInfo(IndexerMode.NORMAL);
    }
    
    private enum IndexerMode {
        NORMAL,
        QUERY,
        INSTALL,
        UNINSTALL
    }
    
    private class IndexerArgsInfo {
        IndexerArgsInfo(IndexerMode mode) {
            this.mode = mode;
        }
        
        String query;
        IndexerMode mode;
    }
    
}
