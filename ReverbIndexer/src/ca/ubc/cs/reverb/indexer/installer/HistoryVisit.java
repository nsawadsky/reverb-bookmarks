package ca.ubc.cs.reverb.indexer.installer;

import java.util.Date;

public class HistoryVisit {
    public long visitId;
    public long locationId;
    public long sessionId;
    public String url;
    public String title;
    public Date visitDate;
    public int visitType;
    public boolean isGoogleSearch = false;
    public WebBrowserType browserType;
    
    /**
     * These attributes cannot be trusted.  The Firefox database frequently has a null
     * value for the from_visit column, even though the page was reached via a link on 
     * another page.
     */
    public long fromVisitId;
    public transient String fromUrl;

    public HistoryVisit() {}
    
    public HistoryVisit(WebBrowserType browserType, long visitId, Date visitDate, int visitType, long sessionId, long locationId, String url, 
            String title, long fromVisitId, String fromUrl) {
        this.visitId = visitId;
        this.locationId = locationId;
        this.sessionId = sessionId;
        this.url = url;
        this.title = title;
        this.visitDate = visitDate;
        this.visitType = visitType;
        this.fromVisitId = fromVisitId;
        this.fromUrl = fromUrl;
        this.browserType = browserType;
    }

    public boolean isRedirect() {
        if (browserType == WebBrowserType.MOZILLA_FIREFOX) {
            if (visitType == FirefoxVisitType.LINK ||
                    visitType == FirefoxVisitType.TYPED ||
                    visitType == FirefoxVisitType.BOOKMARK) {
                return false;
            }
            return true;
        } else if (browserType == WebBrowserType.GOOGLE_CHROME) {
            if ((visitType & 0xF0000000) == 0 || (visitType & ChromeVisitType.CHAIN_END) != 0) {
                int maskedVisitType = (visitType & ChromeVisitType.CORE_MASK);
                if (maskedVisitType == ChromeVisitType.LINK ||
                        maskedVisitType == ChromeVisitType.TYPED ||
                        maskedVisitType == ChromeVisitType.AUTO_BOOKMARK ||
                        maskedVisitType == ChromeVisitType.GENERATED ||
                        maskedVisitType == ChromeVisitType.START_PAGE ||
                        maskedVisitType == ChromeVisitType.FORM_SUBMIT ||
                        maskedVisitType == ChromeVisitType.KEYWORD) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
