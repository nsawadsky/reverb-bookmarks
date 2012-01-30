package ca.ubc.cs.reverb.indexer.study;

import ca.ubc.cs.reverb.indexer.LocationInfo;

public class DeleteLocationEvent extends StudyDataEvent {

    public DeleteLocationEvent(long timestamp, 
            LocationInfo info, float frecencyBoost) {
        super(timestamp, StudyEventType.DELETE_LOCATION, info, frecencyBoost);
    }

}
