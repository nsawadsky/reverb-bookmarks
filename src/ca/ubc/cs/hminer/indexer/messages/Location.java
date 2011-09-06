package ca.ubc.cs.hminer.indexer.messages;

public class Location {
    public Location() {
    }
    
    public Location(String url, String title) {
        this.url = url;
        this.title = title;
    }
    
    public String url;
    public String title;
}   
