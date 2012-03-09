package ca.ubc.cs.reverb.indexer.messages;

public class Location {
    public Location() {
    }
    
    public Location(long id, String url, String title, float relevance, float frecencyBoost, float overallScore) {
        this.url = url;
        this.title = title;
        this.id = id;
        this.relevance = relevance;
        this.frecencyBoost = frecencyBoost;
        this.overallScore = overallScore;
    }

    public String url;
    public String title;
    
    public long id;
    public float relevance;
    public float frecencyBoost;
    public float overallScore;
}   
