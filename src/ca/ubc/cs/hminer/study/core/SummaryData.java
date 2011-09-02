package ca.ubc.cs.hminer.study.core;

public class SummaryData {
    public SummaryData() {}
    
    public SummaryData(ClassifierData classifierData, double classifierAccuracy) {
        this.initialVisitCount = classifierData.initialVisitCount;
        this.redirectVisitCount = classifierData.redirectVisitCount;
        this.overallRevisitRate = classifierData.overallRevisitRate;
        this.googleVisitCount = classifierData.googleVisitCount;
        this.locationsToClassifyCount = classifierData.locationsToClassifyCount;
        this.locationsClassifiedCount = classifierData.locationsClassifiedCount;
        this.codeRelatedLocationCount = classifierData.codeRelatedLocationCount;
        this.nonCodeRelatedLocationCount = classifierData.nonCodeRelatedLocationCount;
        this.unknownLocationCount = classifierData.unknownLocationCount;
        this.classifierAccuracy = classifierAccuracy;
    }
    
    public int initialVisitCount = 0;
    public int redirectVisitCount = 0;
    public double overallRevisitRate = 0.0;
    public int googleVisitCount = 0;
    public int locationsToClassifyCount = 0;
    public int locationsClassifiedCount = 0;
    public int codeRelatedLocationCount;
    public int nonCodeRelatedLocationCount;
    public int unknownLocationCount;
    public double classifierAccuracy = 0.0;
}
