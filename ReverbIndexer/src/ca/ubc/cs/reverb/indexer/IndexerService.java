package ca.ubc.cs.reverb.indexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.Similarity;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.QueryResult;

public class IndexerService {
    private static Logger log = Logger.getLogger(IndexerService.class);

    public static void main(String[] args) {
        IndexerService service = new IndexerService();
        service.start(args);
    }

    public void start(String[] args) {
        BasicConfigurator.configure();
        RootLogger.getRootLogger().setLevel(Level.INFO);

        try {
            IndexerConfig config = new IndexerConfig();
            
            configLucene();
            
            LocationsDatabase locationsDatabase = new LocationsDatabase(config);
            WebPageIndexer indexer = new WebPageIndexer(config, locationsDatabase);
            
            Map<String, String> parsed = parseArgs(args);
            String query = parsed.get("-query");
            if (query != null) {
                runQuery(config, indexer, locationsDatabase, query);
            } else {
                
                IndexPipeListener indexPipeListener = new IndexPipeListener(config, indexer);
                
                QueryPipeListener queryPipeListener = new QueryPipeListener(config, indexer, locationsDatabase);
                
                indexPipeListener.start();
                queryPipeListener.start();
                
                Runtime runtime = Runtime.getRuntime();
                log.info("Indexer service started, max memory = " + runtime.maxMemory()/(1024*1024) + " MB");
            }
        } catch (IndexerException e) {
            log.error("Exception starting service", e);
        }
    }

    private void runQuery(IndexerConfig config, WebPageIndexer indexer, LocationsDatabase locationsDatabase, String query) throws IndexerException {
        SharedIndexReader indexReader;
        try {
            // IndexReader is thread-safe, share it for efficiency.
            indexReader = new SharedIndexReader(IndexReader.open(indexer.getIndexWriter(), true));
        } catch (Exception e) {
            throw new IndexerException("Error creating IndexReader: " + e, e);
        }

        WebPageSearcher searcher = new WebPageSearcher(config, indexReader, locationsDatabase);
        
        List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
        queries.add(new IndexerQuery(query, query));
        BatchQueryResult batchResult = searcher.performSearch(queries);
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
            
            @Override 
            public float coord(int overlap, int maxOverlap) {
                return 1.0F;
            }

        });
        
    }
    
    private Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new HashMap<String, String>();
        for (String arg: args) {
            String[] tokenized = arg.split("=");
            if (tokenized.length != 2) {
                break;
            }
            result.put(tokenized[0], tokenized[1]);
        }
        return result;
    }
    
}
