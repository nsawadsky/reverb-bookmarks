package ca.ubc.cs.hminer.indexer;

import java.util.concurrent.LinkedBlockingQueue;

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
            LinkedBlockingQueue<PageInfo> pagesQueue = new LinkedBlockingQueue<PageInfo>();
            
            indexer = new WebPageIndexer(config, pagesQueue);
            
            indexPipeListener = new IndexPipeListener(config, pagesQueue);
            
            WebPageSearcher searcher = new WebPageSearcher(config);
            queryPipeListener = new QueryPipeListener(config, searcher);
            
            indexer.start();
            indexPipeListener.start();
            queryPipeListener.start();
            
            log.info("Indexer service started");
        } catch (IndexerException e) {
            log.error("Exception starting service", e);
        }
    }
}
