package ca.ubc.cs.hminer.study.core;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class HistoryReport {
    public HistoryReport() {}
    
    public HistoryReport(String participantOccupation, String participantPrimaryProgrammingLanguage, WebBrowserType participantPrimaryWebBrowser,
            Date collectionDate, Date historyStartDate, Date historyEndDate, 
            UUID participantId, SummaryData summaryData, LocationListStats codeRelatedStats,
            List<LocationAndClassification> locationsManuallyClassified, List<Location> locationsClassified, 
            List<HistoryVisit> visitList) {
        this.participantOccupation = participantOccupation;
        this.participantPrimaryProgrammingLanguage = participantPrimaryProgrammingLanguage;
        this.participantPrimaryWebBrowser = participantPrimaryWebBrowser;
        this.collectionDate = collectionDate;
        this.historyStartDate = historyStartDate;
        this.historyEndDate = historyEndDate;
        this.summaryData = summaryData;
        this.codeRelatedStats = codeRelatedStats;
        this.locationsManuallyClassified = locationsManuallyClassified;
        this.locationsClassified = locationsClassified;
        this.visitList = visitList;
        this.participantId = participantId;
    }

    public String participantOccupation;
    public String participantPrimaryProgrammingLanguage;
    public WebBrowserType participantPrimaryWebBrowser;
    public UUID participantId;
    public Date collectionDate;
    
    public Date historyStartDate;
    public Date historyEndDate;

    public SummaryData summaryData;
    public LocationListStats codeRelatedStats;
    public List<LocationAndClassification> locationsManuallyClassified;
    public List<Location> locationsClassified;
    public List<HistoryVisit> visitList;
}
