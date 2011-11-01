package ca.ubc.cs.hminer.study.core;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class HistoryReport {
    public HistoryReport() {}
    
    public HistoryReport(String participantOccupation, String participantPrimaryProgrammingLanguage, OSType osType, WebBrowserType participantPrimaryWebBrowser,
            Date collectionDate, Date historyStartDate, Date historyEndDate, 
            UUID participantId, SummaryData summaryData, LocationListStats codeRelatedStats,
            LocationListStats javadocStats, LocationListStats nonJavadocStats,
            List<LocationAndClassification> locationsManuallyClassified, List<Location> locationsClassified, 
            List<HistoryVisit> visitList) {
        this.participantOccupation = participantOccupation;
        this.participantPrimaryProgrammingLanguage = participantPrimaryProgrammingLanguage;
        this.participantOSType = osType;
        this.participantPrimaryWebBrowser = participantPrimaryWebBrowser;
        this.collectionDate = collectionDate;
        this.historyStartDate = historyStartDate;
        this.historyEndDate = historyEndDate;
        this.summaryData = summaryData;
        this.codeRelatedStats = codeRelatedStats;
        this.javadocStats = javadocStats;
        this.nonJavadocStats = nonJavadocStats;
        this.locationsManuallyClassified = locationsManuallyClassified;
        this.locationsClassified = locationsClassified;
        this.visitList = visitList;
        this.participantId = participantId;
    }

    public String participantOccupation;
    public String participantPrimaryProgrammingLanguage;
    public OSType participantOSType;
    public WebBrowserType participantPrimaryWebBrowser;
    public UUID participantId;
    public Date collectionDate;
    
    public Date historyStartDate;
    public Date historyEndDate;

    public SummaryData summaryData;
    public LocationListStats codeRelatedStats;
    public LocationListStats javadocStats;
    public LocationListStats nonJavadocStats;
    public List<LocationAndClassification> locationsManuallyClassified;
    public List<Location> locationsClassified;
    public List<HistoryVisit> visitList;
}
