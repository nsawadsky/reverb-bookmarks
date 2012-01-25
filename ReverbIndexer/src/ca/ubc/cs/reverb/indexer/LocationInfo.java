package ca.ubc.cs.reverb.indexer;

public class LocationInfo {
    public final static float MAX_FRECENCY_BOOST = 5.0F;
    
    public LocationInfo(long id, String url, long lastVisitTime,
            int visitCount, float frecencyBoost, boolean isJavadoc) {
        this.id = id;
        this.url = url;
        this.lastVisitTime = lastVisitTime;
        this.visitCount = visitCount;
        this.storedFrecencyBoost = frecencyBoost;
        this.isJavadoc = isJavadoc;
    }

    public long id = 0;
    public String url = null;
    public long lastVisitTime = 0;
    public int visitCount = 0;
    /**
     * Stored frecencyBoost is always calculated relative to lastVisitTime.
     */
    public float storedFrecencyBoost = 0.0F;
    public boolean isJavadoc = false;
    
    public float getFrecencyBoost(long now) {
        float result = storedFrecencyBoost * (float)Math.exp(LocationsDatabase.FRECENCY_DECAY * (now - lastVisitTime));
        result = (float)Math.min(result, MAX_FRECENCY_BOOST);
        return result;
    }
}
