package ca.ubc.cs.reverb.eclipseplugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;

import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;

public class StudyUserInterface implements EditorMonitorListener {
    private StudyState studyState;
    private PluginConfig config;
    private PluginLogger logger;
    
    public StudyUserInterface(PluginConfig config, PluginLogger logger) throws PluginException {
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
        OutputStreamWriter writer = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonGenerator jsonGenerator = mapper.getJsonFactory().createJsonGenerator(new File(config.getStudyStatePath()), 
                    JsonEncoding.UTF8);
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

            mapper.writeValue(jsonGenerator, studyState);
        } catch (Exception e) {
            throw new PluginException("Error saving plugin state: " + e, e);
        } finally {
            if (writer != null) {
                try { 
                    writer.close();
                } catch (IOException e) { } 
            }
        }
    }
    
}
