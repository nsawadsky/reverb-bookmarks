package ca.ubc.cs.hminer.study.core;

import java.util.List;

public class StatsCalculator {
    public static LocationListStats calculateStats(List<LocationAndVisits> locations) {
        LocationListStats result = new LocationListStats();
        
        int revisitCountLink = 0;
        int revisitCountTyped = 0;
        int revisitCountBookmark = 0;

        int visitCount = 0;
        int revisitCount = 0;
        for (LocationAndVisits location: locations) {
            List<HistoryVisit> visitList = location.visits;
            visitCount += visitList.size();
            if (visitList.size() > 1) {
                revisitCount += (visitList.size() - 1);
                for (int i = 1; i < visitList.size(); i++) {
                    HistoryVisit visit = visitList.get(i);
                    switch (visit.visitType) {
                    case FirefoxVisitType.LINK: {
                        revisitCountLink++;
                        break;
                    }
                    case FirefoxVisitType.TYPED: {
                        revisitCountTyped++;
                        break;
                    }
                    case FirefoxVisitType.BOOKMARK: {
                        revisitCountBookmark++;
                        break;
                    }
                    default: {
                        break;
                    }
                    }
                }
            }
        }
        
        result.visitCount = visitCount;
        
        if ( visitCount > 0 ) {
            result.revisitRate = (double)revisitCount/visitCount;
        }
        if (revisitCount > 0) { 
            result.revisitFractionLink = (double)revisitCountLink/revisitCount;
            result.revisitFractionTyped = (double)revisitCountTyped/revisitCount;
            result.revisitFractionBookmark = (double)revisitCountBookmark/revisitCount;
        }
        
        return result;
    }
}
