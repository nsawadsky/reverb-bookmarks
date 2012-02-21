package ca.ubc.cs.reverb.indexer.installer;

import java.util.ArrayList;
import java.util.List;

public class LocationAndVisits {
    public HistoryLocation location;
    public List<HistoryVisit> visits = new ArrayList<HistoryVisit>();
    
    public LocationAndVisits(HistoryLocation location) {
        this.location = location;
    }
}
