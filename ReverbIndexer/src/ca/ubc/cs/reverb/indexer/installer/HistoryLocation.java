package ca.ubc.cs.reverb.indexer.installer;

public class HistoryLocation {

    public long id;
    // transient used for fields which must *not* be included in the report that is submitted.
    public transient String url;
    public transient String title;
    public boolean isJavadoc = false;
    
    public HistoryLocation() {}
    
    public HistoryLocation(long id, String url, String title) {
        this.id = id;
        this.url = url;
        this.title = title;
    }

}
