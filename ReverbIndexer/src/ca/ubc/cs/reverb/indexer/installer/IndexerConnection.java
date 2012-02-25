package ca.ubc.cs.reverb.indexer.installer;

import java.io.File;
import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

import ca.ubc.cs.reverb.indexer.IndexerConfig;
import ca.ubc.cs.reverb.indexer.IndexerException;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.UpdatePageInfoRequest;

import xpnp.XpNamedPipe;

public class IndexerConnection {

    private XpNamedPipe pipe;
    
    public IndexerConnection(IndexerConfig config, boolean launchIndexerIfNecessary) throws IndexerException {
        pipe = createPipe(config, launchIndexerIfNecessary);
    }
    
    public void close() {
        if (pipe != null) {
            pipe.close();
            pipe = null;
        }
    }
    
    public void indexPage(UpdatePageInfoRequest info) throws IndexerException {
        sendMessage(info);
    }
    
    private void sendMessage(IndexerMessage msg) throws IndexerException {
        IndexerMessageEnvelope envelope = new IndexerMessageEnvelope(null, msg);
        ObjectMapper mapper = new ObjectMapper();
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
    
    private XpNamedPipe createPipe(IndexerConfig config, boolean launchIndexerIfNecessary) throws IndexerException {
        try {
            try {
                return XpNamedPipe.openNamedPipe("reverb-index", true);
            } catch (IOException e1) { 
                if (!launchIndexerIfNecessary) {
                    throw e1;
                }
                String indexerInstallPath = config.getCurrentIndexerInstallPath();
                File indexerJarPath = new File(indexerInstallPath + File.separator + "ReverbIndexer.jar");
                if (!indexerJarPath.exists()) {
                    throw new IndexerException("Cannot find file '" + indexerJarPath + "'");
                }
                
                Runtime.getRuntime().exec(
                        "javaw.exe -Djava.library.path=native -Xmx1024m -jar ReverbIndexer.jar", 
                        null, new File(indexerInstallPath));

                int tries = 0;
                IOException lastException = null;
                while (tries++ < 10) {
                    try { 
                        Thread.sleep(500);
                        return XpNamedPipe.openNamedPipe("reverb-index", true);
                    } catch (IOException e2) { 
                        lastException = e2;
                    }
                }
                throw lastException;
            }
        } catch (Exception e) {
            throw new IndexerException("Failed to connect to indexer service: " + e, e);
        }
    }
}
