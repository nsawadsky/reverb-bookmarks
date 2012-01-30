package ca.ubc.cs.reverb.indexer.messages;

public class Location {
    public Location() {
    }
    
    public Location(long resultGenTimestamp, String url, String title, float luceneScore, float frecencyBoost, float overallScore) {
        this.resultGenTimestamp = resultGenTimestamp;
        this.url = url;
        this.title = title;
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
    public float luceneScore;
    public float frecencyBoost;
    public float overallScore;
}   
