package ca.ubc.cs.reverb.indexer.messages;

public class Location {
    public Location() {
    }
    
    public Location(long resultGenTimestamp, long id, String url, String title, float luceneScore, float frecencyBoost, float overallScore) {
        this.resultGenTimestamp = resultGenTimestamp;
        this.url = url;
        this.title = title;
        this.id = id;
        this.luceneScore = luceneScore;
        this.frecencyBoost = frecencyBoost;
        this.overallScore = overallScore;
    }

    /**
     * Timestamp when the result set was generated.
     */
    public long resultGenTimestamp;

    public String url;
    public String title;
    
    public long id;
    public float luceneScore;
    public float frecencyBoost;
    public float overallScore;
}   
