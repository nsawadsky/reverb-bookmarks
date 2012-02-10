package ca.ubc.cs.reverb.indexer.study;

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
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import ca.ubc.cs.reverb.indexer.IndexerConfig;
import ca.ubc.cs.reverb.indexer.IndexerException;

public class StudyDataCollector implements Runnable {
    private static Logger log = Logger.getLogger(StudyDataCollector.class);
    
    private final static int LOG_FLUSH_INTERVAL_SECS = 30;
    private final static String UPLOAD_URL = "https://www.cs.ubc.ca/~nicks/reverb/study-data-uploader.php";
    private final static String FILE_INPUT_FIELD_NAME = "uploadedFile";
    private final static String LOG_FILE_STEM = "studydata-";
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
    
    public void start() {
        new Thread(this).start();
    }

    public void logEvent(StudyDataEvent event) {
        synchronized(events) {
            events.add(event);
        }
    }
    
    public void pushDataToServer() throws IndexerException {
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
                    BufferedWriter writer = new BufferedWriter(new FileWriter(currentLogFileInfo.logFile, true));
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
    
}
