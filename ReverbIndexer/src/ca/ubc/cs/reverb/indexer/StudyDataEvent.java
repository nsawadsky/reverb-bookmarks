package ca.ubc.cs.reverb.indexer;

import java.text.SimpleDateFormat;
import java.util.Date;

public class StudyDataEvent {
    private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    
    public StudyDataEvent(long timestamp, StudyEventType eventType, LocationInfo info, float frecencyBoost) {
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
    
    public String getLogLine() {
        return DATE_FORMAT.format(new Date(timestamp)) + 
                ", " + eventType.getShortName() + 
                ", " + locationId + 
                ", " + (isJavadoc ? 1 : 0) +
                ", " + (isCodeRelated ? 1 : 0) +
                ", " + DATE_FORMAT.format(new Date(lastVisitTime)) +
                ", " + visitCount + 
                ", " + String.format("%.3f", storedFrecencyBoost) + 
                ", " + String.format("%.3f", frecencyBoost);
    }
}
