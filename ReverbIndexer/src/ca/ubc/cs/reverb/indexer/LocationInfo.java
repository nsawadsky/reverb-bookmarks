package ca.ubc.cs.reverb.indexer;

public class LocationInfo {
    public final static long VISIT_HALF_LIFE_MSECS = 6 * 30 * 24 * 60 * 60 * 1000L;
    public final static float FRECENCY_DECAY = (float)Math.log(0.5) / VISIT_HALF_LIFE_MSECS;

    public final static float MAX_FRECENCY_BOOST = 5.0F;
    
    public LocationInfo(long id, String url, long lastVisitTime,
            int visitCount, float frecencyBoost, boolean isJavadoc, boolean isCodeRelated) {
        this.id = id;
        this.url = url;
        this.lastVisitTime = lastVisitTime;
        this.visitCount = visitCount;
        this.storedFrecencyBoost = frecencyBoost;
        this.isJavadoc = isJavadoc;
        this.isCodeRelated = isCodeRelated;
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
    public boolean isCodeRelated = false;
    
    /**
     * Returns the frecency boost adjusted for the specified time ('now') and capped at MAX_FRECENCY_BOOST.
     */
    public float getFrecencyBoost(long now) {
        float result = adjustFrecencyBoost(storedFrecencyBoost, lastVisitTime, now);
        result = (float)Math.min(result, MAX_FRECENCY_BOOST);
        return result;
    }
    
    /**
     * Calculates the new frecency boost without capping it at MAX_FRECENCY_BOOST. 
     */
    public static float adjustFrecencyBoost(float startBoost, long startTime, long endTime) {
        return startBoost * (float)Math.exp(FRECENCY_DECAY * (endTime - startTime));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LocationInfo other = (LocationInfo) obj;
        if (id != other.id)
            return false;
        if (isCodeRelated != other.isCodeRelated)
            return false;
        if (isJavadoc != other.isJavadoc)
            return false;
        if (lastVisitTime != other.lastVisitTime)
            return false;
        if (Float.floatToIntBits(storedFrecencyBoost) != Float
                .floatToIntBits(other.storedFrecencyBoost))
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        if (visitCount != other.visitCount)
            return false;
        return true;
    }
    
    
}
