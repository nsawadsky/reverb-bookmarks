package ca.ubc.cs.reverb.indexer;

import java.awt.SystemColor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.spi.RootLogger;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.Similarity;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;

import xpnp.XpNamedPipe;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryRequest;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessageEnvelope;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.messages.ShutdownRequest;
import ca.ubc.cs.reverb.indexer.study.StudyDataCollector;

public class IndexerService {
    private static Logger log = Logger.getLogger(IndexerService.class);

    private IndexerConfig config;
    private IndexerArgsInfo argsInfo;
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

            argsInfo = parseArgs(args);
            
            if (argsInfo.mode == IndexerMode.INSTALL) {
                System.exit(installService());
            }
            
            if (argsInfo.mode == IndexerMode.UNINSTALL) {
                System.exit(uninstallService());
            }

            if (argsInfo.mode == IndexerMode.QUERY) {
                System.exit(runQuery());
            }
            
            // We need config to be initialized before we can configure file logging.
            // Also, for other startup modes, we do not want to compete with the main service
            // for access to the log file.
            configureFileDebugLogging();
            
            configLucene();
            
            StudyDataCollector collector = new StudyDataCollector(config);
            collector.start();
            
            locationsDatabase = new LocationsDatabase(config);
            indexer = new WebPageIndexer(config, locationsDatabase, collector);
            
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

    private int installService() throws IndexerException {
        final Integer[] result = new Integer[] {0};
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override 
                public void run() {
                    try {
                        unregisterAndShutdownService();
                        String installLocation = registerService();
                        log.info("Registered service successfully");
                        if (argsInfo.showUI) { 
                            String message = "The indexer service has been registered at:\n" + installLocation; 
                            showMessageWithWrap(message, "Reverb Indexer Installed", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (IndexerException e) {
                        log.error("Error installing service", e);
                        if (argsInfo.showUI) {
                            showMessageWithWrap("An error occurred during install: " + e, "Install Error", JOptionPane.ERROR_MESSAGE);
                        }
                        result[0] = 1;
                        return;
                    }
                }
            });
        } catch (Exception e) {
            log.error("Error launching install", e);
            return 1;
        }
        return result[0];
    }
    
    private int uninstallService() {
        final Integer[] result = new Integer[] {0};
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override 
                public void run() {
                    try {
                        unregisterAndShutdownService();
                        log.info("Unregistered and shutdown service successfully");
                        if (argsInfo.showUI) { 
                            String message = "The indexer service has been stopped and unregistered.\n\n" +
                                    "To remove the index, as well as all logs and settings, delete the folder:\n\n" + config.getDataPath();
                            showMessageWithWrap(message, "Reverb Indexer Uninstalled", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (IndexerException e) {
                        log.error("Error uninstalling service", e);
                        if (argsInfo.showUI) {
                            showMessageWithWrap("An error occurred during uninstall: " + e, "Uninstall Error", JOptionPane.ERROR_MESSAGE);
                        }
                        result[0] = 1;
                    }
                }
            });
        } catch (Exception e) {
            log.error("Error launching uninstall", e);
            return 1;
        }
        return result[0];
    }
    
    private int runQuery() throws IndexerException {
        String query = argsInfo.query;
        XpNamedPipe indexerConnection = null;
        try {
            indexerConnection = startServiceAndConnect();
            
            List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
            queries.add(new IndexerQuery(query, null));
            BatchQueryRequest request = new BatchQueryRequest(queries);
            
            IndexerMessageEnvelope requestEnvelope = new IndexerMessageEnvelope(null, request);
    
            ObjectMapper mapper = new ObjectMapper();
            byte [] requestBytes = mapper.writeValueAsBytes(requestEnvelope);
            
            indexerConnection.writeMessage(requestBytes);
            
            byte[] replyBytes = indexerConnection.readMessage(10000);
            IndexerMessageEnvelope replyEnvelope = mapper.readValue(replyBytes, IndexerMessageEnvelope.class);

            StringWriter output = new StringWriter();
            JsonGenerator jsonGenerator = mapper.getJsonFactory().createJsonGenerator(output);
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());
            mapper.writeValue(jsonGenerator, replyEnvelope.message);
            System.out.println(output.toString());

            return 0;
        } catch (Exception e) {
            log.error("Error running query", e);
            return 1;
        } finally {
            if (indexerConnection != null) {
                indexerConnection.close();
            }
        }
    }
    
    /**
     * Tries to connect to service.  If it fails, then launches service and tries for up to 5 seconds to connect.
     * 
     * @throw IndexerException If any error occurs. 
     * @return Connection to service.
     */
    private XpNamedPipe startServiceAndConnect() throws IndexerException {
        XpNamedPipe result = null;
        try {
            result = XpNamedPipe.openNamedPipe("reverb-query", true);
        } catch (IOException e) { }
        if (result == null) {
            try {
                Runtime.getRuntime().exec(
                        "javaw.exe -Djava.library.path=native -Xmx1024m -jar ReverbIndexer.jar", 
                        null, new File(config.getCurrentIndexerInstallPath()));
                int tries = 0;
                while (result == null && tries++ < 10) {
                    try { 
                        Thread.sleep(500);
                        result = XpNamedPipe.openNamedPipe("reverb-query", true);
                    } catch (Exception e) { }
                }
            } catch (IndexerException e) { 
                throw e;
            } catch (IOException e) {
                throw new IndexerException("Error launching service: " + e, e);
            }
        }
        if (result == null) {
            throw new IndexerException("Failed to connect to service.");
        }
        return result;
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
        } finally {
            if (pipe != null) {
                pipe.close();
            }
        }
        
    }
    
    /**
     * Registers service and returns the path where service was registered.
     */
    private String registerService() throws IndexerException {
        String installLocation = argsInfo.installLocation;
        if (installLocation == null) {
            installLocation = System.getProperty("user.dir");
            if (installLocation == null) {
                throw new IndexerException("Failed to get current working directory");
            }
        }
        File installPointer = new File(config.getIndexerInstallPointerPath());
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(installPointer), "UTF-8");
            writer.write(installLocation);
            return installLocation;
        } catch (Exception e) {
            throw new IndexerException("Error registering service: " + e, e);
        } finally {
            try {
                writer.close();
            } catch (IOException e) { }
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
    
    private IndexerArgsInfo parseArgs(String[] args) {
        IndexerArgsInfo result = new IndexerArgsInfo();
        
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
            result.mode = IndexerMode.QUERY;
            result.query = argsMap.get("query");
        }
        if (argsMap.containsKey("install")) {
            result.mode = IndexerMode.INSTALL;
            result.installLocation = argsMap.get("install");
        }
        if (argsMap.containsKey("uninstall")) {
            result.mode = IndexerMode.UNINSTALL;
        }
        if (argsMap.containsKey("noui")) {
            result.showUI = false;
        }
        return result;
    }
    
    private enum IndexerMode {
        NORMAL,
        QUERY,
        INSTALL,
        UNINSTALL
    }
    
    private class IndexerArgsInfo {
        IndexerArgsInfo() {
        }
        
        String query;
        IndexerMode mode = IndexerMode.NORMAL;
        boolean showUI = true;
        String installLocation;
    }
    
    private void showMessageWithWrap(String message, String title, int messageType) {
        JTextArea textArea = new JTextArea(message);
        textArea.setColumns(30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setSize(textArea.getPreferredSize().width, 1);
        textArea.setBackground(SystemColor.control);
        textArea.setEditable(false);
        JOptionPane.showMessageDialog(null, textArea, title, messageType);
    }
}
