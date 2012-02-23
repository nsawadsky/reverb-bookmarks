package ca.ubc.cs.reverb.indexer.study;

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
    RECOMMENDATION_CLICK("REC_CLICK"),
    
    /**
     * Delete of a location.
     */
    DELETE_LOCATION("DELETE"),
    
    /**
     * Reached a specified number of locations indexed.
     */
    LOCATIONS_INDEXED_MILESTONE("LOC_INDEXED"),
    
    /**
     * Eclipse plugin view was opened/closed.
     */
    PLUGIN_VIEW_STATE_CHANGED("VIEW_STATE_CHANGE");
    
    private String shortName;
    
    private StudyEventType(String shortName) {
        this.shortName = shortName;
    }
    
    public String getShortName() {
        return shortName;
    }
}
