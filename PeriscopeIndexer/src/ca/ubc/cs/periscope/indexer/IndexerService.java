package ca.ubc.cs.periscope.indexer;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;

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
            
            LocationsDatabase locationsDatabase = new LocationsDatabase(config);
            indexer = new WebPageIndexer(config, locationsDatabase);
            
            indexPipeListener = new IndexPipeListener(config, indexer);
            
            queryPipeListener = new QueryPipeListener(config, indexer);
            
            indexPipeListener.start();
            queryPipeListener.start();
            
            log.info("Indexer service started");
        } catch (IndexerException e) {
            log.error("Exception starting service", e);
        }
    }
}
