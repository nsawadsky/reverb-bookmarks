package ca.ubc.cs.reverb.indexer;

public class LocationInfo {
    public LocationInfo(long id, String url, long lastVisitTime,
            int visitCount, float frecencyBoost, boolean isJavadoc) {
        this.id = id;
        this.url = url;
        this.lastVisitTime = lastVisitTime;
        this.visitCount = visitCount;
        this.frecencyBoost = frecencyBoost;
        this.isJavadoc = isJavadoc;
    }

    public long id = 0;
    public String url = null;
    public long lastVisitTime = 0;
    public int visitCount = 0;
    public float frecencyBoost = 0.0F;
    public boolean isJavadoc = false;
}
