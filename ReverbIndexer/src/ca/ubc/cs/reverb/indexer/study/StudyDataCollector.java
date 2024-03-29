package ca.ubc.cs.reverb.indexer.study;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;

import ca.ubc.cs.reverb.indexer.IndexerConfig;
import ca.ubc.cs.reverb.indexer.IndexerException;

public class StudyDataCollector implements Runnable {
    private static Logger log = Logger.getLogger(StudyDataCollector.class);
    
    private final static int LOG_FLUSH_INTERVAL_SECS = 30;
    private final static String UPLOAD_URL = "https://www.cs.ubc.ca/~nicks/reverb/study-data-uploader.php";
    private final static String FILE_INPUT_FIELD_NAME = "uploadedFile";
    private final static String LOG_FILE_STEM = "reverb-study-data-";
    private final static String LOG_FILE_EXTENSION = ".txt";
    private final static int MAX_LOG_FILE_SIZE_BYTES = 2000000; // 2 MB
    
    private static Pattern LOG_FILE_NAME_PATTERN = Pattern.compile(LOG_FILE_STEM + "([0-9]+)\\.txt");
    
    /**
     * Access to log files must be synchronized on this reference.  If both events and fileLock must
     * be locked, must make sure to acquire fileLock first.
     */
    private Object fileLock = new Object();
    
    /**
     * Access must be synchronized on the events reference.  If both events and fileLock must
     * be locked, must make sure to acquire fileLock first.
     */
    private List<StudyDataEvent> events = new ArrayList<StudyDataEvent>();
    
    /**
     * Access must be synchronized on the events reference.  If both events and fileLock must
     * be locked, must make sure to acquire fileLock first.
     */
    private IndexerStudyState studyState;

    private IndexerConfig config; 

    public StudyDataCollector(IndexerConfig config) throws IndexerException {
        this.config = config;
        loadStudyState();
    }
    
    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while (true) {
            try {
                Thread.sleep(LOG_FLUSH_INTERVAL_SECS * 1000);
            } catch (InterruptedException e) {
            }
            try {
                flushEventsToFile(false);
            } catch (IOException e) {
                // This exception is logged by flushEventsToFile().
            }
        }
        
    }
    
    public void start() {
        new Thread(this).start();
    }

    public void logEvent(StudyDataEvent event) {
        synchronized(events) {
            if (studyState.isDataCollectionEnabled) {
                events.add(event);
            }
        }
    }
    
    public void pushDataToServer(boolean isFinalUpload) throws IndexerException {
        try {
            flushEventsToFile(true);
            
            List<LogFileInfo> logFiles = getLogFileInfos();
            
            if (logFiles.size() < 2) {
                return;
            }
            
            // Do not send the current log file.
            logFiles.remove(logFiles.size() - 1);
            
            HttpClient httpClient = new DefaultHttpClient();
            
            try {
                for (LogFileInfo logFileInfo: logFiles) {
                    File logFile = logFileInfo.logFile;
                    byte[] data = new byte[(int)logFile.length()];
                    FileInputStream inputStream = new FileInputStream(logFile);
                    try {
                        inputStream.read(data);
                    } finally {
                        inputStream.close();
                    }
                    
                    File zipFile = File.createTempFile("reverb", ".zip");
                    try {
                        ZipOutputStream zipOutput = new ZipOutputStream(new FileOutputStream(zipFile));
                        try {
                            ZipEntry entry = new ZipEntry(logFile.getName());
                            
                            zipOutput.putNextEntry(entry);
                            
                            zipOutput.write(data);
                        } finally {
                            zipOutput.close();
                        }
                        
                        HttpPost httpPost = new HttpPost(UPLOAD_URL);
                        
                        MultipartEntity requestEntity = new MultipartEntity();
        
                        StringBody participantId = new StringBody(config.getUserId());
                        requestEntity.addPart("participant", participantId);
                        
                        StringBody key = new StringBody(config.getUserIdKey());
                        requestEntity.addPart("key", key);

                        FileBody fileInputPart = new FileBody(zipFile);
                        requestEntity.addPart(FILE_INPUT_FIELD_NAME, fileInputPart);
                        
                        httpPost.setEntity(requestEntity);
        
                        HttpResponse response = httpClient.execute(httpPost);
                        StatusLine line = response.getStatusLine();
                        EntityUtils.consume(response.getEntity());
                        if (line.getStatusCode() == HttpStatus.SC_OK) {
                            logFile.delete();
                            if (isFinalUpload) {
                                setDataCollectionEnabled(false);
                            }
                        } else {
                            throw new IndexerException(Integer.toString(line.getStatusCode()) + " upload response: " + 
                                    line.getReasonPhrase());
                        }
                    } finally {
                        zipFile.delete();
                    }
                }
            } finally {
                httpClient.getConnectionManager().shutdown();
            }
        } catch (IndexerException e) {
            throw e;
        } catch (Exception e) {
            throw new IndexerException("Error while trying to push data to server: " + e, e);
        } 
    }
    
    private void setDataCollectionEnabled(boolean enabled) throws IndexerException {
        synchronized(events) {
            studyState.isDataCollectionEnabled = enabled;
            saveStudyState();
        }
    }
    
    private void flushEventsToFile(boolean forceCreateNewFile) throws IOException {
        synchronized (fileLock) {
            try {
                List<StudyDataEvent> eventsToFlush = new ArrayList<StudyDataEvent>();
                synchronized (events) {
                    // Remove the events to be flushed from the event list.
                    eventsToFlush.addAll(events);
                    events.clear();
                }
                LogFileInfo currentLogFileInfo = getCurrentLogFileInfo();
                if (eventsToFlush.size() > 0) {
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(currentLogFileInfo.logFile, true), "UTF-8"));
                    try {
                        for (StudyDataEvent event: eventsToFlush) {
                            writeEvent(writer, event);
                        }
                    } finally {
                        writer.close();
                    }
                }
                long fileSize = currentLogFileInfo.logFile.length();
                if ((fileSize > 0 && forceCreateNewFile) || fileSize >= MAX_LOG_FILE_SIZE_BYTES) {
                    createNewLogFile(currentLogFileInfo);
                }
            } catch (IOException e) {
                log.error("Error writing to study data log file", e);
                throw e;
            }
        }
    }
    
    private void createNewLogFile(LogFileInfo currentLogFileInfo) throws IOException {
        synchronized (fileLock) {
            File newFile = getLogFilePath(currentLogFileInfo.logFileIndex + 1);
            if (!newFile.createNewFile()) {
                throw new IOException("Failed to create file: " + newFile.getAbsolutePath());
            }
        }
    }
    
    private void writeEvent(BufferedWriter writer, StudyDataEvent event) throws IOException {
        synchronized (fileLock) {
            writer.write(event.getLogLine());
            writer.newLine();
        }
    }
    
    private LogFileInfo getCurrentLogFileInfo() {
        List<LogFileInfo> logFiles = getLogFileInfos();
        if (logFiles.isEmpty()) {
            return new LogFileInfo(getLogFilePath(1), 1);
        }
        return logFiles.get(logFiles.size()-1);
    }

    private File getLogFilePath(int index) {
        return new File(config.getStudyDataLogFolderPath() + File.separator + 
                LOG_FILE_STEM + Integer.toString(index) + LOG_FILE_EXTENSION);
    }
    
    /**
     * Get existing log files, in ascending order (most recent last).
     */
    private List<LogFileInfo> getLogFileInfos() {
        File logFolder = new File(config.getStudyDataLogFolderPath());
        final List<LogFileInfo> result = new ArrayList<LogFileInfo>();
        logFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                Matcher matcher = LOG_FILE_NAME_PATTERN.matcher(file.getName());
                if (matcher.matches() && matcher.group(1) != null) {
                    result.add(new LogFileInfo(file, Integer.parseInt(matcher.group(1))));
                }
                return false;
            }
        });
        Collections.sort(result, new Comparator<LogFileInfo>() {

            @Override
            public int compare(LogFileInfo file1, LogFileInfo file2) {
                return new Integer(file1.logFileIndex).compareTo(file2.logFileIndex);
            }
            
        });
        return result;
    }
    
    private class LogFileInfo {
        public File logFile;
        public int logFileIndex;

        public LogFileInfo(File logFile, int logFileIndex) {
            this.logFile = logFile;
            this.logFileIndex = logFileIndex;
        }
    }
    
    private void loadStudyState() throws IndexerException {
        try {
            File indexerStudyStateFile = new File(config.getIndexerStudyStatePath());
            if (!indexerStudyStateFile.exists()) {
                studyState = new IndexerStudyState();
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            studyState = mapper.readValue(indexerStudyStateFile, IndexerStudyState.class);
        } catch (Exception e) {
            throw new IndexerException("Error loading indexer study state: " + e, e);
        }
    }
    
    private void saveStudyState() throws IndexerException {
        JsonGenerator jsonGenerator = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            jsonGenerator = mapper.getJsonFactory().createJsonGenerator(new File(config.getIndexerStudyStatePath()), 
                    JsonEncoding.UTF8);
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

            mapper.writeValue(jsonGenerator, studyState);
        } catch (Exception e) {
            throw new IndexerException("Error saving indexer study state: " + e, e);
        } finally {
            if (jsonGenerator != null) {
                try { 
                    jsonGenerator.close();
                } catch (IOException e) { } 
            }
        }
    }
    
}
