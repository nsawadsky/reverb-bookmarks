package ca.ubc.cs.reverb.indexer.study;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class StudyDataEvent {
    private final static String DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    
    public StudyDataEvent(long timestamp, StudyEventType eventType) {
        this.timestamp = timestamp;
        this.eventType = eventType;
    }
    
    public long timestamp;
    public StudyEventType eventType;

    public static DateFormat getDateFormat() {
        // Not cached, since DateFormat and its subclasses are not thread-safe.
        SimpleDateFormat result = new SimpleDateFormat(DATE_FORMAT_STRING);
        result.setTimeZone(TimeZone.getTimeZone("GMT"));
        return result;
    }
    
    public String getLogLine() {
        return getDateFormat().format(new Date(timestamp)) + ", " + eventType.getShortName(); 
    }
    
}
