package ca.ubc.cs.hminer.study.core;

import java.util.List;

public class ClassifierData {
    public ClassifierData(int initialVisitCount, int redirectVisitCount,
            double overallRevisitRate, int googleVisitCount,
            int locationsToClassifyCount, int locationsClassifiedCount,
            int codeRelatedLocationCount, int javadocLocationCount, int nonCodeRelatedLocationCount, int unknownLocationCount,
            List<LocationAndVisits> codeRelatedLocations, 
            List<Location> locationsClassified, 
            List<HistoryVisit> visitList) {
        this.initialVisitCount = initialVisitCount;
        this.redirectVisitCount = redirectVisitCount;
        this.overallRevisitRate = overallRevisitRate;
        this.googleVisitCount = googleVisitCount;
        this.locationsToClassifyCount = locationsToClassifyCount;
        this.locationsClassifiedCount = locationsClassifiedCount;
        this.codeRelatedLocationCount = codeRelatedLocationCount;
        this.javadocLocationCount = javadocLocationCount;
        this.nonCodeRelatedLocationCount = nonCodeRelatedLocationCount;
        this.unknownLocationCount = unknownLocationCount;
        this.codeRelatedLocations = codeRelatedLocations;
        this.locationsClassified = locationsClassified;
        this.visitList = visitList;
    }

    public int initialVisitCount = 0;
    public int redirectVisitCount = 0;
    public double overallRevisitRate = 0.0;
    public int googleVisitCount = 0;
    public int locationsToClassifyCount = 0;
    public int locationsClassifiedCount = 0;
    public int codeRelatedLocationCount;
    public int javadocLocationCount;
    public int nonCodeRelatedLocationCount;
    public int unknownLocationCount;
    public List<LocationAndVisits> codeRelatedLocations;
    public List<Location> locationsClassified;
    public List<HistoryVisit> visitList;
}
    
