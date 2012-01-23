package ca.ubc.cs.reverb.indexer;

public class StudyDataEvent {
    public StudyDataEvent(long timestamp, StudyEventType eventType,
            long locationId) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.locationId = locationId;
    }
    
    
    public StudyDataEvent(long timestamp, StudyEventType eventType,
            long locationId, boolean isJavadoc) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.locationId = locationId;
        this.isJavadoc = isJavadoc;
    }

    public long timestamp;
    public StudyEventType eventType;
    public long locationId;
    public boolean isJavadoc;
}
