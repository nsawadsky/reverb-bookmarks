package ca.ubc.cs.reverb.eclipseplugin.reports;

import ca.ubc.cs.reverb.indexer.messages.Location;

public class LocationRating {
    public LocationRating() { }

    public LocationRating(Location location) {
        this.locationId = location.id;
        
        this.url = location.url;
        this.title = location.title;
        
        this.luceneScore = location.luceneScore;
        this.frecencyBoost = location.frecencyBoost;
        this.overallScore = location.overallScore;
        
        this.resultGenTimestamp = location.resultGenTimestamp;
    }
    
    /**
     * Timestamp when the result set was generated.
     */
    public long resultGenTimestamp;

    // Tagging these two fields with the transient attribute ensures that they are not included 
    // in the Jackson-generated JSON report which is uploaded to the server.
    public transient String url;
    public transient String title;

    public long locationId;
    public float luceneScore;
    public float frecencyBoost;
    public float overallScore;
    
    public int rating = 0;
    public String comment;
}