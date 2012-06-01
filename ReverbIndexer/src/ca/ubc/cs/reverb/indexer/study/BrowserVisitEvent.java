package ca.ubc.cs.reverb.indexer.study;

import ca.ubc.cs.reverb.indexer.LocationInfo;

public class BrowserVisitEvent extends LocationDataEvent {

    public BrowserVisitEvent(long timestamp, 
            LocationInfo info, float frecencyBoost) {
        super(timestamp, StudyEventType.BROWSER_VISIT, info, frecencyBoost);
    }

}
