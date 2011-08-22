package ca.ubc.cs.hminer.study.core;

import java.util.Date;

public class HistoryVisit {
    public long visitId;
    public long locationId;
    public long sessionId;
    public transient String url;
    public transient String title;
    public Date visitDate;
    public int visitType;
    public boolean isGoogleSearch = false;
    
    /**
     * This attribute cannot be trusted.  The Firefox database frequently has a null
     * value for the from_visit column, even though the page was reached via a link on 
     * another page.
     */
    public transient String fromUrl;

    public HistoryVisit() {}
    
    public HistoryVisit(long visitId, Date visitDate, int visitType, long sessionId, long locationId, String url, 
            String title, String fromUrl) {
        this.visitId = visitId;
        this.locationId = locationId;
        this.sessionId = sessionId;
        this.url = url;
        this.title = title;
        this.visitDate = visitDate;
        this.visitType = visitType;
        this.fromUrl = fromUrl;
    }
    
}
