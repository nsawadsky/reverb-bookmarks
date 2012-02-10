package ca.ubc.cs.reverb.indexer.study;

import java.util.Date;

import ca.ubc.cs.reverb.indexer.LocationInfo;

public abstract class LocationDataEvent extends StudyDataEvent {
    
    public LocationDataEvent(long timestamp, StudyEventType eventType, LocationInfo info, float frecencyBoost) {
        super(timestamp, eventType);

        this.locationId = info.id;
        this.isJavadoc = info.isJavadoc;
        this.isCodeRelated = info.isCodeRelated;
        this.lastVisitTime = info.lastVisitTime;
        this.visitCount = info.visitCount;
        this.storedFrecencyBoost = info.storedFrecencyBoost;
        this.frecencyBoost = frecencyBoost;
    }

    public long locationId;
    public long lastVisitTime;
    public int visitCount;
    public float storedFrecencyBoost;
    public float frecencyBoost;
    public boolean isJavadoc;
    public boolean isCodeRelated;
    
    public String getLogLine() {
        return super.getLogLine() + 
                ", " + locationId + 
                ", " + (isJavadoc ? 1 : 0) +
                ", " + (isCodeRelated ? 1 : 0) +
                ", " + getDateFormat().format(new Date(lastVisitTime)) +
                ", " + visitCount + 
                ", " + String.format("%.3f", storedFrecencyBoost) + 
                ", " + String.format("%.3f", frecencyBoost);
    }
}
