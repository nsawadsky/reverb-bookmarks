package ca.ubc.cs.reverb.indexer.study;

import java.text.SimpleDateFormat;
import java.util.Date;

import ca.ubc.cs.reverb.indexer.LocationInfo;

public abstract class LocationDataEvent {
    private final static String DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    
    public LocationDataEvent(long timestamp, StudyEventType eventType, LocationInfo info, float frecencyBoost) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.locationId = info.id;
        this.isJavadoc = info.isJavadoc;
        this.isCodeRelated = info.isCodeRelated;
        this.lastVisitTime = info.lastVisitTime;
        this.visitCount = info.visitCount;
        this.storedFrecencyBoost = info.storedFrecencyBoost;
        this.frecencyBoost = frecencyBoost;
    }

    public long timestamp;
    public StudyEventType eventType;
    public long locationId;
    public long lastVisitTime;
    public int visitCount;
    public float storedFrecencyBoost;
    public float frecencyBoost;
    public boolean isJavadoc;
    public boolean isCodeRelated;
    
    public static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat(DATE_FORMAT_STRING);
    }
    
    public String getLogLine() {
        SimpleDateFormat dateFormat = getDateFormat();
        return dateFormat.format(new Date(timestamp)) + 
                ", " + eventType.getShortName() + 
                ", " + locationId + 
                ", " + (isJavadoc ? 1 : 0) +
                ", " + (isCodeRelated ? 1 : 0) +
                ", " + dateFormat.format(new Date(lastVisitTime)) +
                ", " + visitCount + 
                ", " + String.format("%.3f", storedFrecencyBoost) + 
                ", " + String.format("%.3f", frecencyBoost);
    }
}
