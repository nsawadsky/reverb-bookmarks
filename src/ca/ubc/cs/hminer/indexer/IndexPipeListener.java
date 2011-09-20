package ca.ubc.cs.hminer.indexer;

import org.codehaus.jackson.map.ObjectMapper;
import npw.WindowsNamedPipe;

import org.apache.log4j.Logger;

import ca.ubc.cs.hminer.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.hminer.indexer.messages.PageInfo;


public class IndexPipeListener implements Runnable {
    private static Logger log = Logger.getLogger(IndexPipeListener.class);

    private static final int LISTENING_THREADS = 5;
    
    private IndexerConfig config;
    private String indexPipeName;
    private WebPageIndexer indexer;
    
    public IndexPipeListener(IndexerConfig config, WebPageIndexer indexer) throws IndexerException  {
        this.config = config;
        this.indexer = indexer;
        this.indexPipeName = WindowsNamedPipe.makePipeName("historyminer-index", true);
        if (indexPipeName == null) {
            throw new IndexerException("Failed to make index pipe name: " + WindowsNamedPipe.getErrorMessage());
        }
    }
    
    public void start() {
        for (int i = 0; i < LISTENING_THREADS; i++) {
            new Thread(new ListenerInstance(config, indexPipeName, indexer)).start();
        }
    }
   
    private class ListenerInstance implements Runnable {
        private IndexerConfig config;
        private String indexPipeName;
        private WebPageIndexer indexer;
        
        public ListenerInstance(IndexerConfig config, String indexPipeName, WebPageIndexer indexer) {
            this.config = config;
            this.indexPipeName = indexPipeName;
            this.indexer = indexer;
        }
        
        public void run() {
            long pipeHandle = WindowsNamedPipe.createPipe(indexPipeName, true);
            if (pipeHandle == 0) {
                log.error("Failed to create pipe '" + indexPipeName + "': " + WindowsNamedPipe.getErrorMessage());
                return;
            }
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                
                ObjectMapper mapper = new ObjectMapper();
                while (true) {
                    if (!WindowsNamedPipe.connectPipe(pipeHandle)) {
                        log.error("Failed to connect index pipe: " + WindowsNamedPipe.getErrorMessage());
                        return;
                    }
                    
                    byte[] data = null;
                    while ((data = WindowsNamedPipe.readPipe(pipeHandle)) != null) {
                        PageInfo info = null;
                        try {
                            IndexerMessageEnvelope envelope = mapper.readValue(data, IndexerMessageEnvelope.class);
                            if (envelope.message == null) {
                                throw new IndexerException("envelope.message is null");
                            }
                            if (!(envelope.message instanceof PageInfo)) {
                                throw new IndexerException("Unexpected message content: " + envelope.message.getClass());
                            }
                            info = (PageInfo)envelope.message;
                        } catch (Exception e) {
                            log.error("Exception parsing message from index pipe", e);
                        }
                        if (info != null) {
                            log.info("Got page: " + info.url);
                            try {
                                indexer.indexPage(info);
                            } catch (Exception e) {
                                log.error("Error indexing page '" + info.url + "'", e);
                            }
                        }
                    }
                    
                    log.info("Error reading index pipe: " + WindowsNamedPipe.getErrorMessage());
                    
                    if (!WindowsNamedPipe.disconnectPipe(pipeHandle)) {
                        log.error("Failed to disconnect index pipe: " + WindowsNamedPipe.getErrorMessage());
                        return;
                    }
                }
            } finally {
                WindowsNamedPipe.closePipe(pipeHandle);
            }
        }
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        
    }
}
