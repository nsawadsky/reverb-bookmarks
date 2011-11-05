package ca.ubc.cs.reverb.indexer.messages;

public class Location {
    public Location() {
    }
    
    public Location(String url, String title, float luceneScore, float frecencyBoost, float overallScore) {
        this.url = url;
        this.title = title;
        this.luceneScore = luceneScore;
        this.frecencyBoost = frecencyBoost;
        this.overallScore = overallScore;
    }
    
    public String url;
    public String title;
    public float luceneScore;
    public float frecencyBoost;
    public float overallScore;
}   
