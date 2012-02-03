package ca.ubc.cs.reverb.eclipseplugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import ca.ubc.cs.reverb.eclipseplugin.views.UploadLogsDialog;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.UploadLogsReply;
import ca.ubc.cs.reverb.indexer.messages.UploadLogsRequest;

/**
 * Threading: We assume this class is only ever invoked from the UI thread.
 */
public class StudyActivityMonitor implements EditorMonitorListener {
    // 30 seconds
    private final static int UPLOAD_PROMPT_DELAY_MSECS = 30000;
    //private final static int UPLOAD_PROMPT_DELAY_MSECS = 10000;
    // 15 minutes
    private final static int UPLOAD_RETRY_DELAY_MSECS = 15 * 60 * 1000;
    //private final static int UPLOAD_RETRY_DELAY_MSECS = 15 * 1000;
    
    private IndexerConnection indexerConnection;
    private StudyState studyState;
    private PluginConfig config;
    private PluginLogger logger;
    private long createdThreadId;
    private Shell shell;
    
    public StudyActivityMonitor(Shell shell, PluginConfig config, PluginLogger logger, IndexerConnection indexerConnection) throws PluginException {
        this.createdThreadId = Thread.currentThread().getId();
        this.config = config;
        this.logger = logger;
        this.shell = shell;
        this.indexerConnection = indexerConnection;
        loadStudyState();
        if (studyState.uploadPending) {
            schedulePromptForUpload(UPLOAD_PROMPT_DELAY_MSECS);
        }
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
            if (!studyState.uploadPending && studyState.activeIntervals % StudyState.LOG_UPLOAD_INTERVALS == 0) {
                studyState.uploadPending = true;
                schedulePromptForUpload(UPLOAD_PROMPT_DELAY_MSECS);
            }
            try {
                saveStudyState();
            } catch (PluginException e) {
                logger.logError("Error saving study state", e);
            }
        }
        
    }
    
    public List<Location> getRecommendationsClicked() {
        return new ArrayList<Location>(studyState.recommendationsClicked);
    }
    
    public void addRecommendationClicked(Location clicked) {
        // Log a warning if multiple threads are using a single instance of this class.
        if (Thread.currentThread().getId() != createdThreadId) {
            logger.logWarn("StudyActivityMonitor.addRecommendationClicked invoked from different thread than called constructor");
        }
        studyState.recommendationsClicked.add(clicked);
        try {
            saveStudyState();
        } catch (PluginException e) {
            logger.logError("Error saving study state", e);
        }
    }

    private void schedulePromptForUpload(int delayMsecs) {
        shell.getDisplay().timerExec(delayMsecs, new Runnable() {

            @Override
            public void run() {
                promptForUpload();
            }
            
        });
    }
    
    private void handleSuccessfulUpload() {
        studyState.uploadPending = false;
        studyState.successfulLogUploads++;
        try {
            saveStudyState();
        } catch (PluginException e) {
            logger.logError("Error saving study state", e);
        }
    }
    
    private void promptForUpload() {
        UploadLogsDialog uploadDialog = new UploadLogsDialog(shell, config, logger);
        if (uploadDialog.open() != Dialog.OK) {
            schedulePromptForUpload(UPLOAD_RETRY_DELAY_MSECS);
        } else {
            Job job = new Job("Uploading Reverb logs") {
                @Override
                protected IStatus run(IProgressMonitor arg0) {
                    IStatus result = Status.OK_STATUS;
                    try {
                        UploadLogsReply reply = indexerConnection.sendUploadLogsRequest(
                                new UploadLogsRequest(), 60000);
                        if (reply.errorOccurred) {
                            result = logger.createStatus(IStatus.ERROR, reply.errorMessage, null);
                        } 
                    } catch (Exception e) {
                        result = logger.createStatus(IStatus.ERROR, "Error uploading logs", e);
                    }
                    if (result.isOK()) {
                        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
                            public void run() { handleSuccessfulUpload(); }
                        });
                    } else {
                        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
                            public void run() { schedulePromptForUpload(UPLOAD_RETRY_DELAY_MSECS); }
                        });
                    }
                    return result;
                }
            };
            job.schedule();
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
