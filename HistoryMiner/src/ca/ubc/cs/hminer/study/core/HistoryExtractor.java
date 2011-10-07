package ca.ubc.cs.hminer.study.core;

import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

public abstract class HistoryExtractor {
    private static Logger log = Logger.getLogger(HistoryExtractor.class);   
    
    public static HistoryExtractor getHistoryExtractor(WebBrowserType browserType) throws HistoryMinerException {
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
    
    public abstract Date getEarliestVisitDate() throws HistoryMinerException;
    
    public abstract List<HistoryVisit> extractHistory(Date startDate, Date endDate) throws HistoryMinerException;    
    
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
