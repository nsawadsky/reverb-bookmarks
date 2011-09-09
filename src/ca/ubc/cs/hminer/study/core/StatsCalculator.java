package ca.ubc.cs.hminer.study.core;

import java.util.List;

public class StatsCalculator {
    private static class RevisitCounts {
        public int visitCount = 0;
        public int revisitCount = 0;
        public int revisitCountLink = 0;
        public int revisitCountTyped = 0;
        public int revisitCountBookmark = 0;
    }
    
    public static LocationListStats calculateStats(List<LocationAndVisits> locations,
            WebBrowserType webBrowserType) {
        LocationListStats result = new LocationListStats();
        
        RevisitCounts revisitCounts = getRevisitCounts(locations, webBrowserType);
        
        result.visitCount = revisitCounts.visitCount;
        
        if ( revisitCounts.visitCount > 0 ) {
            result.revisitRate = (double)revisitCounts.revisitCount/revisitCounts.visitCount;
        }
        if (revisitCounts.revisitCount > 0) { 
            result.revisitFractionLink = (double)revisitCounts.revisitCountLink/revisitCounts.revisitCount;
            result.revisitFractionTyped = (double)revisitCounts.revisitCountTyped/revisitCounts.revisitCount;
            result.revisitFractionBookmark = (double)revisitCounts.revisitCountBookmark/revisitCounts.revisitCount;
        }
        
        return result;
    }
    
    private static RevisitCounts getRevisitCounts(List<LocationAndVisits> locations, WebBrowserType webBrowserType) {
        if (webBrowserType == WebBrowserType.MOZILLA_FIREFOX) {
            return getRevisitCountsFirefox(locations);
        }
        return getRevisitCountsChrome(locations);
    }
    
    private static RevisitCounts getRevisitCountsFirefox(List<LocationAndVisits> locations) {
        RevisitCounts result = new RevisitCounts();
        for (LocationAndVisits location: locations) {
            List<HistoryVisit> visitList = location.visits;
            result.visitCount += visitList.size();
            if (visitList.size() > 1) {
                result.revisitCount += (visitList.size() - 1);
                for (int i = 1; i < visitList.size(); i++) {
                    HistoryVisit visit = visitList.get(i);
                    switch (visit.visitType) {
                    case FirefoxVisitType.LINK: {
                        result.revisitCountLink++;
                        break;
                    }
                    case FirefoxVisitType.TYPED: {
                        result.revisitCountTyped++;
                        break;
                    }
                    case FirefoxVisitType.BOOKMARK: {
                        result.revisitCountBookmark++;
                        break;
                    }
                    default: {
                        break;
                    }
                    }
                }
            }
        }
        return result;
    }
    
    private static RevisitCounts getRevisitCountsChrome(List<LocationAndVisits> locations) {
        RevisitCounts result = new RevisitCounts();
        for (LocationAndVisits location: locations) {
            List<HistoryVisit> visitList = location.visits;
            result.visitCount += visitList.size();
            if (visitList.size() > 1) {
                result.revisitCount += (visitList.size() - 1);
                for (int i = 1; i < visitList.size(); i++) {
                    HistoryVisit visit = visitList.get(i);
                    int maskedVisitType = (visit.visitType & ChromeVisitType.CORE_MASK);
                    switch (maskedVisitType) {
                    case ChromeVisitType.LINK: 
                    case ChromeVisitType.FORM_SUBMIT: {
                        result.revisitCountLink++;
                        break;
                    }
                    case ChromeVisitType.TYPED: 
                    case ChromeVisitType.GENERATED: 
                    case ChromeVisitType.KEYWORD: {
                        result.revisitCountTyped++;
                        break;
                    }
                    case ChromeVisitType.AUTO_BOOKMARK:
                    case ChromeVisitType.START_PAGE: {
                        result.revisitCountBookmark++;
                        break;
                    }
                    default: {
                        break;
                    }
                    }
                }
            }
        }
        return result;
    }
}
