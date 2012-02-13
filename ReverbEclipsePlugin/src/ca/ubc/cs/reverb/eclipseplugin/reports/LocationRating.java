package ca.ubc.cs.reverb.eclipseplugin.reports;

import ca.ubc.cs.reverb.eclipseplugin.StudyState.LocalUseOnly;
import ca.ubc.cs.reverb.indexer.messages.Location;
import org.codehaus.jackson.map.annotate.JsonView;

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

    // Ensure these two fields are not included in the Jackson-generated JSON report which is uploaded to the server.
    @JsonView(LocalUseOnly.class)
    public transient String url;
    @JsonView(LocalUseOnly.class)
    public transient String title;

    public long locationId;
    public float luceneScore;
    public float frecencyBoost;
    public float overallScore;
    
    public int rating = 0;
    public String comment;
}
