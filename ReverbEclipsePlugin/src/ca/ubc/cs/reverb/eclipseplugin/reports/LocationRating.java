package ca.ubc.cs.reverb.eclipseplugin.reports;

import ca.ubc.cs.reverb.eclipseplugin.StudyState.LocalUseOnly;
import ca.ubc.cs.reverb.indexer.messages.Location;
import org.codehaus.jackson.map.annotate.JsonView;

public class LocationRating {
    public LocationRating() { }

    public LocationRating(Location location, long resultGenTimestamp) {
        this.locationId = location.id;
        
        this.url = location.url;
        this.title = location.title;
        
        this.relevance = location.relevance;
        this.frecencyBoost = location.frecencyBoost;
        this.overallScore = location.overallScore;
        
        this.resultGenTimestamp = resultGenTimestamp;
    }
    
    /**
     * Timestamp when the result set was generated.
     */
    public long resultGenTimestamp;

    // Ensure these two fields are not included in the Jackson-generated JSON report which is uploaded to the server.
    @JsonView(LocalUseOnly.class)
    public String url;
    @JsonView(LocalUseOnly.class)
    public String title;

    public long locationId;
    public float relevance;
    public float frecencyBoost;
    public float overallScore;
    
    public int rating = 0;
    public String comment;
}
