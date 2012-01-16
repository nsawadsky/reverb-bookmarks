package ca.ubc.cs.hminer.study.core;

import java.util.ArrayList;
import java.util.List;

public class LocationAndVisits {
    public Location location;
    public List<HistoryVisit> visits = new ArrayList<HistoryVisit>();
    
    public LocationAndVisits(Location location) {
        this.location = location;
    }
}
