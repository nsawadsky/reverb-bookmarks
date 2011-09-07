package ca.ubc.cs.hminer.study.core;

import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

public abstract class HistoryExtractor {
    private static Logger log = Logger.getLogger(HistoryExtractor.class);   
    
    public static HistoryExtractor getHistoryExtractor(WebBrowserType browserType) throws HistoryMinerException {
        switch (browserType) {
        case GOOGLE_CHROME: {
            return new ChromeHistoryExtractor();
        } 
        default: {
            return new FirefoxHistoryExtractor();
        }
        }
    }
    
    public abstract Date getEarliestVisitDate() throws HistoryMinerException;
    
    public abstract List<HistoryVisit> extractHistory(Date startDate, Date endDate) throws HistoryMinerException;    
    
    protected boolean isWindowsOS() {
        String os = System.getProperty("os.name");
        return (os != null && os.contains("Windows"));
    }
    
}
