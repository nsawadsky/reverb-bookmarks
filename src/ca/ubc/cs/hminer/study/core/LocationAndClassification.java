package ca.ubc.cs.hminer.study.core;

import org.codehaus.jackson.map.annotate.JsonView;

public class LocationAndClassification {
    public long locationId;
    public LocationType manualClassification;
    
    @JsonView(AnonymizePartial.class)
    public String url;

    @JsonView(AnonymizePartial.class)
    public String title;
    
    public static class AnonymizePartial{};
    
    public LocationAndClassification() {}
    
    public LocationAndClassification(long id,
            LocationType manualClassification, String url, String title) {
        this.locationId = id;
        this.manualClassification = manualClassification;
        this.url = url;
        this.title = title;
    }
    
    
}
