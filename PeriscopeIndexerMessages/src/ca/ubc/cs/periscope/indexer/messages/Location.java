package ca.ubc.cs.periscope.indexer.messages;

public class Location {
    public Location() {
    }
    
    public Location(String url, String title, float luceneScore) {
        this.url = url;
        this.title = title;
        this.luceneScore = luceneScore;
    }
    
    public String url;
    public String title;
    public float luceneScore;
    public float frecencyBoost;
    public float overallScore;
}   
