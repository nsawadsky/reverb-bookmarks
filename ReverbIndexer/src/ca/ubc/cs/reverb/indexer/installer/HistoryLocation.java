package ca.ubc.cs.reverb.indexer.installer;

public class HistoryLocation {

    public long id;
    public String url;
    public String title;
    
    public HistoryLocation() {}
    
    public HistoryLocation(long id, String url, String title) {
        this.id = id;
        this.url = url;
        this.title = title;
    }

}
