package ca.ubc.cs.reverb.indexer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

import ca.ubc.cs.reverb.indexer.StudyDataEvent.Field;

public class StudyDataCollector implements Runnable {
    private final static int LOG_FLUSH_INTERVAL_SECS = 30;
    private final static String RECORD_SEPARATOR = "=======================================================";
    private final static String UPLOAD_URL = "https://www.cs.ubc.ca/~nicks/reverb/uploader.php";
    private final static String FILE_INPUT_NAME = "uploadedFile";
    
    private static Logger log = Logger.getLogger(StudyDataCollector.class);
    
    private Object fileLock = new Object();
    
    private Object eventsLock = new Object();
    
    /**
     * Access must be synchronized on the eventsLock reference.
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
            flushEventsToFile();
        }
        
    }

    public void logEvent(StudyDataEvent event) {
        synchronized(eventsLock) {
            events.add(event);
        }
    }
    
    public void flushEventsToFile() {
        try {
            List<StudyDataEvent> eventsToFlush = null;
            synchronized (eventsLock) {
                eventsToFlush = events;
                events = new ArrayList<StudyDataEvent>();
            }
            if (eventsToFlush.size() > 0) {
                try {
                    synchronized (fileLock) {
                        File logFile = new File(config.getStudyDataLogFilePath());
                        BufferedWriter writer = new BufferedWriter(new FileWriter(logFile));
                        try {
                            for (StudyDataEvent event: eventsToFlush) {
                                writeEvent(writer, event);
                            }
                            writer.flush();
                            events.clear();
                        } finally {
                            writer.close();
                        }
                    }
                } catch (Exception e) {
                    synchronized(eventsLock) {
                        eventsToFlush.addAll(events);
                        events = eventsToFlush;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error writing to study data log file: " + e, e);
        }
    }
    
    public void pushDataToServer() throws IndexerException {
        File zipFile = null;
        HttpClient httpClient = null;
        try {
            synchronized(fileLock) {
                flushEventsToFile();
                
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
            error = e;
        } finally {
            if (zipFile != null) { zipFile.delete(); }
            if (httpClient !=null) { httpClient.getConnectionManager().shutdown(); }
        }
    }
    
    private void writeEvent(BufferedWriter writer, StudyDataEvent event) throws IOException {
        writer.write(RECORD_SEPARATOR);
        writer.newLine();
        for (Field field: event.getFields()) {
            writer.write(field.fieldName);
            writer.write("=");
            writer.write(field.fieldName);
            writer.newLine();
        }
    }
    
}