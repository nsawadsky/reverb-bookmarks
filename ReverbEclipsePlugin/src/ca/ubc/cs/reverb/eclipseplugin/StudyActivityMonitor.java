package ca.ubc.cs.reverb.eclipseplugin;

import java.io.File;
import java.io.IOException;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;

import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;
import ca.ubc.cs.reverb.indexer.messages.Location;

/**
 * Threading: We assume this class is only ever invoked from the UI thread.
 */
public class StudyActivityMonitor implements EditorMonitorListener {
    private StudyState studyState;
    private PluginConfig config;
    private PluginLogger logger;
    private long createdThreadId;
    
    public StudyActivityMonitor(PluginConfig config, PluginLogger logger) throws PluginException {
        this.createdThreadId = Thread.currentThread().getId();
        this.config = config;
        this.logger = logger;
        loadStudyState();
    }
    
    @Override
    public void onCodeQueryReply(CodeQueryReply reply) {
        
    }

    @Override
    public void onInteractionEvent(long timeMsecs) {
        long currentInterval = timeMsecs / StudyState.ACTIVITY_INTERVAL_MSECS;
        if (currentInterval != studyState.lastActiveInterval) {
            studyState.lastActiveInterval = currentInterval;
            studyState.activeIntervals++;
            try {
                saveStudyState();
            } catch (PluginException e) {
                logger.logError("Error saving study state", e);
            }
        }
        
    }
    
    public void addRecommendationClicked(Location clicked) {
        // Log a warning if multiple threads are using a single instance of this class.
        if (Thread.currentThread().getId() != createdThreadId) {
            logger.logWarn("StudyActivityMonitor.addRecommendationClicked invoked from different thread than was used to create it");
        }
        studyState.recommendationsClicked.add(clicked);
        try {
            saveStudyState();
        } catch (PluginException e) {
            logger.logError("Error saving study state", e);
        }
    }

    private void loadStudyState() throws PluginException {
        try {
            File pluginStateFile = new File(config.getStudyStatePath());
            if (!pluginStateFile.exists()) {
                studyState = new StudyState();
                saveStudyState();
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            studyState = mapper.readValue(pluginStateFile, StudyState.class);
        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException("Error loading plugin state: " + e, e);
        }
    }
    
    private void saveStudyState() throws PluginException {
        JsonGenerator jsonGenerator = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            jsonGenerator = mapper.getJsonFactory().createJsonGenerator(new File(config.getStudyStatePath()), 
                    JsonEncoding.UTF8);
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

            mapper.writeValue(jsonGenerator, studyState);
        } catch (Exception e) {
            throw new PluginException("Error saving plugin state: " + e, e);
        } finally {
            if (jsonGenerator != null) {
                try { 
                    jsonGenerator.close();
                } catch (IOException e) { } 
            }
        }
    }
    
}
