package ca.ubc.cs.reverb.indexer;

public class RecommendationEvent extends StudyDataEvent {

    public RecommendationEvent(long timestamp,
            LocationInfo info, float frecencyBoost, float relevance, float overallScore) {
        super(timestamp, StudyEventType.RECOMMENDATION, info, frecencyBoost);
        this.relevance = relevance;
        this.overallScore = overallScore;
    }
    
    public float relevance;
    public float overallScore;
    
    public String getLogLine() {
        return super.getLogLine() + ", " + String.format("%.3f", relevance) + 
                ", " + String.format("%.3f", overallScore);
    }

}
