package ca.ubc.cs.reverb.indexer;

public enum StudyEventType {
    /**
     * Recommendation.
     */
    RECOMMENDATION("REC"),
    
    /** 
     * Browser visit.
     */
    BROWSER_VISIT("BROWSE"),
    
    /**
     * Click on recommendation in IDE plugin.
     */
    RECOMMENDATION_CLICK("REC_CLICK");
    
    private String shortName;
    
    private StudyEventType(String shortName) {
        this.shortName = shortName;
    }
    
    public String getShortName() {
        return shortName;
    }
}
