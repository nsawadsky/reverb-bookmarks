package ca.ubc.cs.hminer.study.core;

import java.util.Calendar;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;

public class HistoryMiner {

    /*
SELECT datetime(visits.visit_date/1000000, 'unixepoch', 'localtime'), places.url, places.title, visits.visit_type, visits.from_visit, from_places.url
FROM moz_historyvisits AS visits JOIN moz_places AS places ON visits.place_id = places.id 
LEFT OUTER JOIN moz_historyvisits AS from_visits ON visits.from_visit = from_visits.id 
LEFT OUTER JOIN moz_places AS from_places ON from_visits.place_id = from_places.id 
WHERE visits.visit_date > strftime('%s', '2011-07-01', 'utc') * 1000000
AND visits.visit_date < strftime('%s', '2011-08-31', 'utc') * 1000000
ORDER BY visits.visit_date DESC
     */
    
    private static Logger log = Logger.getLogger(HistoryMiner.class);
  
    /**
     * @param args
     */
    public static void main(String[] args) {
        BasicConfigurator.configure();
        RootLogger.getRootLogger().setLevel(Level.INFO);
        
        Calendar startDate = Calendar.getInstance();
        startDate.set(2010, Calendar.NOVEMBER, 1);
        Calendar endDate = Calendar.getInstance();
        endDate.set(2010, Calendar.DECEMBER, 31);
        
        try {
            HistoryExtractor extractor = new HistoryExtractor();
            List<HistoryVisit> visits = extractor.extractHistory(startDate.getTime(), endDate.getTime());
            log.info("Total visits = " + visits.size());

            HistoryClassifier classifier = new HistoryClassifier(visits);
            
            classifier.startClassifying();
            
            int prevLocationsClassified = -1;
            do {
                Thread.sleep(4000);
                log.info("Classified " + classifier.getLocationsClassifiedCount() + 
                        " of " + classifier.getLocationsToClassifyCount() + " locations");
                if (classifier.getLocationsClassifiedCount() == prevLocationsClassified) {
                    break;
                }
                prevLocationsClassified = classifier.getLocationsClassifiedCount();
            } while (true);
            
            classifier.shutdown();
            
            ClassifierData classifierResults = classifier.getResults();
            
            LocationListStats codeRelatedStats = StatsCalculator.calculateStats(classifierResults.codeRelatedLocations);
            
            log.info("Total code-related visits = " + codeRelatedStats.visitCount);
            log.info("Code-related revisit rate = " + codeRelatedStats.revisitRate);
                   
            log.info("LINK revisit fraction: " + codeRelatedStats.revisitFractionLink);
            log.info("TYPED revisit fraction: " + codeRelatedStats.revisitFractionTyped);
            log.info("BOOKMARK revisit fraction: " + codeRelatedStats.revisitFractionBookmark);
            
        } catch (Exception e) {
            log.error("Exception processing history", e); 
        }

    }

}
