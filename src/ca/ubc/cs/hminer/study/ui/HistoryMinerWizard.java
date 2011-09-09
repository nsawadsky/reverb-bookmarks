package ca.ubc.cs.hminer.study.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;
import org.codehaus.jackson.map.SerializationConfig;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.IPageChangingListener;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.dialogs.PageChangingEvent;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.custom.BusyIndicator;

import ca.ubc.cs.hminer.study.core.ClassifierData;
import ca.ubc.cs.hminer.study.core.HistoryClassifier;
import ca.ubc.cs.hminer.study.core.HistoryExtractor;
import ca.ubc.cs.hminer.study.core.HistoryMinerData;
import ca.ubc.cs.hminer.study.core.HistoryMinerException;
import ca.ubc.cs.hminer.study.core.HistoryReport;
import ca.ubc.cs.hminer.study.core.HistoryVisit;
import ca.ubc.cs.hminer.study.core.LocationListStats;
import ca.ubc.cs.hminer.study.core.StatsCalculator;
import ca.ubc.cs.hminer.study.core.SummaryData;

public class HistoryMinerWizard extends Wizard implements IPageChangingListener, IPageChangedListener {
    private final static String UPLOAD_URL = "https://www.cs.ubc.ca/~nicks/dbha/uploader.php";
    private final static String FILE_INPUT_NAME = "uploadedFile";
    
    private HistoryMinerData historyMinerData = new HistoryMinerData();
    
    // be careful not to shadow field name in parent class
    private boolean myCanFinish = false;
    private boolean testMode = false;
    private boolean closeBrowserRequested = false;
    private Date earliestVisitDate = new Date();
    
    public HistoryMinerWizard(boolean testMode) {
        setWindowTitle("Developer Browsing History Analyzer");
        this.testMode = testMode;
    }
    
    public boolean isTestMode() {
        return testMode;
    }
    
    @Override
    public void addPages() {
        this.addPage(new WelcomePage());
        this.addPage(new StartEndDatePage());
        this.addPage(new AnalyzeHistoryPage());
        this.addPage(new ClassifyVisitsPage());
        this.addPage(new SubmitPage());
        
        WizardDialog dialog = (WizardDialog)getContainer();
        dialog.addPageChangingListener(this);
        dialog.addPageChangedListener(this);

    }

    @Override
    public void pageChanged(PageChangedEvent event) {
        HistoryMinerWizardPage page = (HistoryMinerWizardPage)event.getSelectedPage();
        if (page != null) {
            page.onPageOpened();
        }
    }

    @Override
    public void handlePageChanging(PageChangingEvent event) {
        HistoryMinerWizardPage pageClosing = (HistoryMinerWizardPage)event.getCurrentPage();
        HistoryMinerWizardPage pageOpening = (HistoryMinerWizardPage)event.getTargetPage();
        
        if (pageClosing != null) {
            pageClosing.onPageClosing();
        }
        
        if (pageOpening != null) {
            pageOpening.onPageOpening();
        }
        
    }
    
    public boolean handleNextPressed() {
        HistoryMinerWizardPage currentPage = (HistoryMinerWizardPage)getContainer().getCurrentPage();
        return currentPage.onNextPressed();
    }

    @Override
    public boolean performFinish() {
        class ReportUploader implements Runnable {
            public Exception error = null;
            public boolean invalidParticipantId = false;
            public boolean maxUploadsReached = false;
            
            public void run() {
                File zipFile = null;
                try {
                    String report = generateReport();
                    ZipOutputStream zipOutput = null;
                    
                    try {
                        zipFile = File.createTempFile("dbha", ".zip");
                        zipOutput = new ZipOutputStream(new FileOutputStream(zipFile));
                        
                        ZipEntry entry = new ZipEntry(historyMinerData.participantId.toString() + ".txt");
                        
                        zipOutput.putNextEntry(entry);
                        
                        byte[] data = report.getBytes("UTF-8");
                        
                        zipOutput.write(data);
                        
                    } finally {
                        if (zipOutput != null) { zipOutput.close(); }
                    }
                    
                    HttpClient httpClient = new DefaultHttpClient();
                    HttpPost httpPost = new HttpPost(UPLOAD_URL);
                    
                    MultipartEntity requestEntity = new MultipartEntity();

                    StringBody participantId = new StringBody(historyMinerData.participantId.toString());
                    requestEntity.addPart("participant", participantId);
                    
                    FileBody fileInputPart = new FileBody(zipFile);
                    requestEntity.addPart(FILE_INPUT_NAME, fileInputPart);
                    
                    httpPost.setEntity(requestEntity);

                    HttpResponse response = httpClient.execute(httpPost);
                    StatusLine line = response.getStatusLine();
                    if (line.getStatusCode() != HttpStatus.SC_OK) {
                        if (line.getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                            invalidParticipantId = true;
                        } else if (line.getStatusCode() == HttpStatus.SC_CONFLICT) {
                            maxUploadsReached = true;
                        }
                        throw new HistoryMinerException(Integer.toString(line.getStatusCode()) + " upload response: " + 
                                line.getReasonPhrase());
                    }
                } catch (Exception e) {
                    error = e;
                } finally {
                    if (zipFile != null) { zipFile.delete(); }
                }
            }
        }
        
        ReportUploader reportUploader = new ReportUploader();
        
        BusyIndicator.showWhile(getShell().getDisplay(), reportUploader);

        if (reportUploader.error == null) {
            MessageDialog.openInformation(getShell(), "Report Uploaded", "Report uploaded successfully.");
            return true;
        } else if (reportUploader.invalidParticipantId) {
            MessageDialog.openError(getShell(), "Error Uploading Report", 
                    "Your participant ID appears to be invalid.  Please contact study coordinators for assistance.");
            return true;
        } else if (reportUploader.maxUploadsReached) {
            MessageDialog.openError(getShell(), "Error Uploading Report", 
                    "The maximum number of uploads for this participant ID has been reached.  Please click 'View Report' to save a copy of " +
                    "the report.  Then zip the report file and email it to the study coordinators.");
            return false;
        } else {
            MessageDialog.openError(getShell(), "Error Uploading Report", 
                    "An error occurred while uploading the report.  Please click 'View Report' to save a copy of " +
                    "the report.  Then zip the report file and email it to the study coordinators.\n\n" + 
                    "Error details: " + reportUploader.error);
            return false;
        }
    }

    @Override 
    public boolean canFinish() {
        return myCanFinish;
    }
    
    public void setSubmitButtonEnabled(boolean enabled) {
        myCanFinish = enabled;
        getContainer().updateButtons();
    }
    
    @Override 
    public boolean isHelpAvailable() {
        return false;
    }
    
    @Override 
    public boolean needsProgressMonitor() {
        return false;
    }

    public HistoryMinerData getHistoryMinerData() {
        return historyMinerData;
    }
    
    public Date getEarliestVisitDate() {
        return earliestVisitDate;
    }

    public void setEarliestVisitDate(Date earliestVisitDate) {
        this.earliestVisitDate = earliestVisitDate;
    }

    public boolean getCloseBrowserRequested() {
        return closeBrowserRequested;
    }

    public void setCloseBrowserRequested(boolean closeBrowserRequested) {
        this.closeBrowserRequested = closeBrowserRequested;
    }

    public String generateReport() throws IOException {
        ClassifierData classifierData = historyMinerData.classifierData;
        LocationListStats codeRelatedStats = StatsCalculator.calculateStats(
                classifierData.codeRelatedLocations, historyMinerData.participantPrimaryWebBrowser);
        
        HistoryReport historyReport = new HistoryReport(historyMinerData.participantOccupation, 
                historyMinerData.participantPrimaryProgrammingLanguage, HistoryExtractor.getOSType(), historyMinerData.participantPrimaryWebBrowser, new Date(), historyMinerData.historyStartDate, historyMinerData.historyEndDate,
                historyMinerData.participantId, new SummaryData(classifierData, historyMinerData.classifierAccuracy), codeRelatedStats, 
                historyMinerData.locationsManuallyClassified, classifierData.locationsClassified, classifierData.visitList);
        
        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);
        JsonGenerator jsonGenerator = mapper.getJsonFactory().createJsonGenerator(writer);
        jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

        mapper.writeValue(jsonGenerator, historyReport);
        
        return writer.toString();
        
    }
    
    public HistoryExtractor getHistoryExtractor() throws HistoryMinerException {
        return HistoryExtractor.getHistoryExtractor(historyMinerData.participantPrimaryWebBrowser);
    }
    
    public HistoryClassifier getHistoryClassifier(List<HistoryVisit> visitList) throws HistoryMinerException {
        return new HistoryClassifier(visitList, historyMinerData.participantPrimaryWebBrowser);
    }

}
