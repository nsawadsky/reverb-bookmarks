package ca.ubc.cs.hminer.study.core;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class HistoryMinerData {
    public String participantOccupation;
    public String participantPrimaryProgrammingLanguage;
    public WebBrowserType participantPrimaryWebBrowser = WebBrowserType.MOZILLA_FIREFOX;
    public Date historyStartDate;
    public Date historyEndDate;
    public ClassifierData classifierData;
    public List<LocationAndClassification> locationsManuallyClassified;
    public double classifierAccuracy = 0.0;
    public UUID participantId;
}
