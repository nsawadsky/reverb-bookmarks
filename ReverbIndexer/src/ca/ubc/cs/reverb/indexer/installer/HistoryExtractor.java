package ca.ubc.cs.reverb.indexer.installer;

import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import ca.ubc.cs.reverb.indexer.IndexerException;

public abstract class HistoryExtractor {
    private static Logger log = Logger.getLogger(HistoryExtractor.class);   
    
    public static HistoryExtractor getHistoryExtractor(WebBrowserType browserType) throws IndexerException {
        switch (browserType) {
        case GOOGLE_CHROME: 
        case CHROMIUM: {
            return new ChromeHistoryExtractor(browserType == WebBrowserType.CHROMIUM);
        } 
        default: {
            return new FirefoxHistoryExtractor();
        }
        }
    }
    
    public abstract Date getEarliestVisitDate() throws IndexerException;
    
    public abstract List<HistoryVisit> extractHistory(Date startDate, Date endDate) throws IndexerException;    
    
    public static OSType getOSType() {
        String os = System.getProperty("os.name");
        OSType osType = OSType.WINDOWS_VISTA_OR_LATER;
        if (os != null) {
            if (os.contains("Windows XP")) {
                osType = OSType.WINDOWS_XP;
            } else if (os.contains("Mac")) {
                osType = OSType.MAC;
            } else if (os.contains("Linux")) {
                osType = OSType.LINUX;
            }
        }
        return osType;
    }
    
}
