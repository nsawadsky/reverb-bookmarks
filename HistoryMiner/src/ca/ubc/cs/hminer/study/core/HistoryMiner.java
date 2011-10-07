package ca.ubc.cs.hminer.study.core;

import java.util.Calendar;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;

public class HistoryMiner {

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
            HistoryExtractor extractor = HistoryExtractor.getHistoryExtractor(WebBrowserType.MOZILLA_FIREFOX);
            List<HistoryVisit> visits = extractor.extractHistory(startDate.getTime(), endDate.getTime());
            log.info("Total visits = " + visits.size());

            HistoryClassifier classifier = new HistoryClassifier(visits, WebBrowserType.MOZILLA_FIREFOX);
            
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
            
            LocationListStats codeRelatedStats = StatsCalculator.calculateStats(classifierResults.codeRelatedLocations,
                    WebBrowserType.MOZILLA_FIREFOX);
            
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
