package ca.ubc.cs.reverb.indexer;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.Similarity;

public class IndexerService {
    private static Logger log = Logger.getLogger(IndexerService.class);
    
    private IndexerConfig config;
    private IndexPipeListener indexPipeListener;
    private QueryPipeListener queryPipeListener;
    private WebPageIndexer indexer;

    public static void main(String[] args) {
        IndexerService service = new IndexerService();
        service.start();
    }
    
    public void start() {
        BasicConfigurator.configure();
        RootLogger.getRootLogger().setLevel(Level.INFO);

        try {
            this.config = new IndexerConfig();
            
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

            });
            
            LocationsDatabase locationsDatabase = new LocationsDatabase(config);
            indexer = new WebPageIndexer(config, locationsDatabase);
            
            indexPipeListener = new IndexPipeListener(config, indexer);
            
            queryPipeListener = new QueryPipeListener(config, indexer, locationsDatabase);
            
            indexPipeListener.start();
            queryPipeListener.start();
            
            Runtime runtime = Runtime.getRuntime();
            log.info("Indexer service started, max memory = " + runtime.maxMemory()/(1024*1024) + " MB");
        } catch (IndexerException e) {
            log.error("Exception starting service", e);
        }
    }
}
