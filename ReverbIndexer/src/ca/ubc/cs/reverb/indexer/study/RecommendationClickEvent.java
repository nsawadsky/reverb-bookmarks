package ca.ubc.cs.reverb.indexer.study;

import ca.ubc.cs.reverb.indexer.LocationInfo;

public class RecommendationClickEvent extends StudyDataEvent {

    public RecommendationClickEvent(long timestamp, 
            LocationInfo info, float frecencyBoost, float relevance, float overallScore, long recommendationTimestamp) {
        super(timestamp, StudyEventType.RECOMMENDATION_CLICK, info, frecencyBoost);
        this.relevance = relevance;
        this.overallScore = overallScore;
        this.recommendationTimestamp = recommendationTimestamp;
    }

    public float relevance;
    public float overallScore;
    public long recommendationTimestamp;

    public String getLogLine() {
        return super.getLogLine() + ", " + String.format("%.3f", relevance) + 
                ", " + String.format("%.3f", overallScore) + 
                ", " + getDateFormat().format(recommendationTimestamp);
    }
}
