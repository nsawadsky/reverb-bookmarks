package ca.ubc.cs.hminer.study.core;

public class LocationAndClassification {
    public long locationId;
    public LocationType manualClassification;
    
    public transient String url;

    public transient String title;
    
    public LocationAndClassification() {}
    
    public LocationAndClassification(long id,
            LocationType manualClassification, String url, String title) {
        this.locationId = id;
        this.manualClassification = manualClassification;
        this.url = url;
        this.title = title;
    }
    
    
}
