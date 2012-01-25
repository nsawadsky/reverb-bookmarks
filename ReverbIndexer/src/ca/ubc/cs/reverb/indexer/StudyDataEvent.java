package ca.ubc.cs.reverb.indexer;

public class StudyDataEvent {
    public StudyDataEvent(long timestamp, StudyEventType eventType, LocationInfo info, float frecencyBoost) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.locationId = info.id;
        this.isJavadoc = info.isJavadoc;
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
    
    public String getLogLine() {
        return Long.toString(timestamp) + 
                ", " + eventType.getShortName() + 
                ", " + locationId + 
                ", " + (isJavadoc ? 1 : 0) +
                ", " + lastVisitTime +
                ", " + visitCount + 
                ", " + String.format("%.3f", storedFrecencyBoost) + 
                ", " + String.format("%.3f", frecencyBoost);
    }
}
