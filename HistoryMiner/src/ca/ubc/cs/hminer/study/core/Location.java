package ca.ubc.cs.hminer.study.core;

public class Location {
    public long id;
    // transient used for fields which must *not* be included in the report that is submitted.
    public transient String url;
    public transient String title;
    public LocationType locationType = LocationType.UNKNOWN;
    
    public Location() {}
    
    public Location(long id, String url, String title) {
        this.id = id;
        this.url = url;
        this.title = title;
    }

}
