package ca.ubc.cs.reverb.indexer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
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
import org.apache.log4j.Logger;

public class StudyDataCollector implements Runnable {
    private static Logger log = Logger.getLogger(StudyDataCollector.class);
    
    private final static int LOG_FLUSH_INTERVAL_SECS = 30;
    private final static String UPLOAD_URL = "https://www.cs.ubc.ca/~nicks/reverb/uploader.php";
    private final static String FILE_INPUT_FIELD_NAME = "uploadedFile";
    private final static String LOG_FILE_STEM = "studydata-";
    private final static String LOG_FILE_EXTENSION = ".txt";
    private final static int MAX_LOG_FILE_BYTES = 2 * 1024 * 1024; // 2 MB
    
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
    
    private IndexerConfig config; 

    public StudyDataCollector(IndexerConfig config) {
        this.config = config;
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

    public void logEvent(StudyDataEvent event) {
        synchronized(events) {
            events.add(event);
        }
    }
    
    public void pushDataToServer() throws IndexerException {
        // Block updates to the file while we are trying to upload it.
        synchronized(fileLock) {
            File zipFile = null;
            HttpClient httpClient = null;
            try {
                flushEventsToFile(true);
                
                File logFile = new File(config.getStudyDataLogFilePath());
                byte[] data = new byte[(int)logFile.length()];
                FileInputStream inputStream = new FileInputStream(logFile);
                inputStream.read(data);
                ZipOutputStream zipOutput = null;
                
                try {
                    zipFile = File.createTempFile("reverb", ".zip");
                    zipOutput = new ZipOutputStream(new FileOutputStream(zipFile));
                    
                    ZipEntry entry = new ZipEntry(config.getUserId() + ".txt");
                    
                    zipOutput.putNextEntry(entry);
                    
                    byte[] data = report.getBytes("UTF-8");
                    
                    zipOutput.write(data);
                    
                } finally {
                    if (zipOutput != null) { zipOutput.close(); }
                }
                
                httpClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(UPLOAD_URL);
                
                MultipartEntity requestEntity = new MultipartEntity();
    
                StringBody participantId = new StringBody(historyMinerData.participantId.toString());
                requestEntity.addPart("participant", participantId);
                
                FileBody fileInputPart = new FileBody(zipFile);
                requestEntity.addPart(FILE_INPUT_NAME, fileInputPart);
                
                httpPost.setEntity(requestEntity);
    
                HttpResponse response = httpClient.execute(httpPost);
                StatusLine line = response.getStatusLine();
                if (line.getStatusCode() != HttpStatus.SC_OK) {
                    if (line.getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                        invalidParticipantId = true;
                    } else if (line.getStatusCode() == HttpStatus.SC_CONFLICT) {
                        maxUploadsReached = true;
                    }
                    throw new HistoryMinerException(Integer.toString(line.getStatusCode()) + " upload response: " + 
                            line.getReasonPhrase());
                }
            }
        } catch (Exception e) {
            throw new IndexerException("Error while trying to push data to server: " + e, e);
        } finally {
            if (zipFile != null) { zipFile.delete(); }
            if (httpClient !=null) { httpClient.getConnectionManager().shutdown(); }
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
                if (eventsToFlush.size() > 0) {
                    LogFileInfo currentLogFileInfo = getCurrentLogFileInfo();
                    BufferedWriter writer = new BufferedWriter(new FileWriter(currentLogFileInfo.logFile));
                    try {
                        for (StudyDataEvent event: eventsToFlush) {
                            writeEvent(writer, event);
                        }
                    } finally {
                        writer.close();
                    }
                    if (forceCreateNewFile || currentLogFileInfo.logFile.length() >= MAX_LOG_FILE_BYTES) {
                        createNewLogFile(currentLogFileInfo);
                    }
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
        String line = Long.toString(event.timestamp) + 
                ", " + event.eventType.getShortName() + 
                ", " + event.locationId + 
                ", " + Integer.toString(event.isJavadoc ? 1 : 0);
        writer.write(line);
        writer.newLine();
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
    
}
