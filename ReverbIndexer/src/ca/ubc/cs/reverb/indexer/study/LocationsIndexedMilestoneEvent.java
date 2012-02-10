package ca.ubc.cs.reverb.indexer.study;

public class LocationsIndexedMilestoneEvent extends StudyDataEvent {

    public LocationsIndexedMilestoneEvent(long timestamp, long currlocationId) {
        super(timestamp, StudyEventType.LOCATIONS_INDEXED_MILESTONE);
        this.currentlocationId = currlocationId;
    }

    public long currentlocationId = 0;
    
    @Override
    public String getLogLine() {
        return super.getLogLine() + ", " + currentlocationId;
    }
}
