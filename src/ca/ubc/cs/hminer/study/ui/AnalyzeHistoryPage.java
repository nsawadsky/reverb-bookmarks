package ca.ubc.cs.hminer.study.ui;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Label;

import ca.ubc.cs.hminer.study.core.HistoryClassifier;
import ca.ubc.cs.hminer.study.core.HistoryExtractor;
import ca.ubc.cs.hminer.study.core.HistoryMinerData;
import ca.ubc.cs.hminer.study.core.HistoryMinerException;
import ca.ubc.cs.hminer.study.core.HistoryVisit;
import ca.ubc.cs.hminer.study.core.Location;
import ca.ubc.cs.hminer.study.core.LocationType;
import ca.ubc.cs.hminer.study.core.VisitType;

public class AnalyzeHistoryPage extends HistoryMinerWizardPage implements SelectionListener {
    private final static Logger log = Logger.getLogger(AnalyzeHistoryPage.class);

    private final static String CLICK_NEXT = "Click Next to begin analyzing history.";
    
    private ProgressBar progressBar;
    private Label instructionLabel;
    private Label progressBarLabel;
    private boolean historyAnalyzed = false;
    
    /**
     * Create the wizard.
     */
    public AnalyzeHistoryPage() {
        super("wizardPage");
        setTitle("Analyze Browsing History");
        setDescription("");
        setPageComplete(true);
    }

    /**
     * Create contents of the wizard.
     * @param parent
     */
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        container.setLayout(new GridLayout(1, false));
        
        instructionLabel = new Label(container, SWT.NONE);
        GridData instructionLabelGridData = new GridData(SWT.LEFT, SWT.TOP, true, true, 1, 1);
        instructionLabelGridData.widthHint = 565;
        instructionLabel.setLayoutData(instructionLabelGridData);
        instructionLabel.setText(CLICK_NEXT);
        
        progressBarLabel = new Label(container, SWT.CENTER);
        progressBarLabel.setAlignment(SWT.CENTER);
        GridData progressBarLabelGridData = new GridData(SWT.CENTER, SWT.TOP, true, false, 1, 1); 
        progressBarLabelGridData.widthHint = 482;
        progressBarLabel.setLayoutData(progressBarLabelGridData);
        
        progressBar = new ProgressBar(container, SWT.NONE);
        GridData gd_progressBar = new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1);
        gd_progressBar.widthHint = 482;
        gd_progressBar.heightHint = 30;
        progressBar.setLayoutData(gd_progressBar);
        
        Label lblNewLabel_1 = new Label(container, SWT.NONE);
        lblNewLabel_1.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true, 1, 1));
    }

    @Override
    public void widgetSelected(SelectionEvent event) {
    }
    
    @Override 
    public boolean onNextPressed() {
        if (historyAnalyzed) {
            return true; 
        } else {
            analyzeHistory();
            return false;
        }
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
        // Should not be called.
    }
    
    private void analyzeHistory() {
        try {
            setBackButtonEnabled(false);
            setPageComplete(false);

            progressBar.setMaximum(100);
            progressBar.setSelection(5);
            progressBarLabel.setText("Analyzing browsing history ...");
            progressBarLabel.pack();
            
            HistoryMinerData data = getHistoryMinerData();
            
            // Note that the values in the wizard may change (if user hits Back button and adjusts start/end dates).
            // Need to capture the values we are using for this extraction.
            Date startDate = data.historyStartDate;
            Date endDate =  data.historyEndDate;
    
            HistoryExtractor extractor = (getHistoryMinerWizard().isTestMode() ? 
                    getMockHistoryExtractor() : new HistoryExtractor());
            
            List<HistoryVisit> visitList = extractor.extractHistory(startDate, endDate);
    
            if (getHistoryMinerWizard().getCloseBrowserRequested()) {
                instructionLabel.setText("You can reopen Firefox while the analysis is in progress.");
            }
            progressBar.setSelection(15);
            
            final HistoryClassifier classifier = (getHistoryMinerWizard().isTestMode() ? 
                    getMockClassifier(visitList) : new HistoryClassifier(visitList)); 
            
            classifier.startClassifying();
            
            getShell().getDisplay().timerExec(1000, new Runnable() {
                private int prevLocationsClassified = 0;
                private int iterationCount = 0;
                
                @Override 
                public void run() {
                    double fractionComplete = 100.0;
                    if ( classifier.getLocationsToClassifyCount() > 0 ) {
                        fractionComplete = (double)classifier.getLocationsClassifiedCount() / 
                                classifier.getLocationsToClassifyCount();
                    }
                    progressBar.setSelection(15 + (int)(fractionComplete * 80));
                    iterationCount++;
                    
                    boolean done = classifier.isDone();
                    
                    if (!done) {
                        if (iterationCount % 4 == 0) {
                            if (classifier.getLocationsClassifiedCount() == prevLocationsClassified) {
                                done = true;
                            } else {
                                prevLocationsClassified = classifier.getLocationsClassifiedCount();
                            }
                        }
                    }
                    if (done) {
                        classifier.shutdown();
                        
                        getHistoryMinerData().classifierData = classifier.getResults();
                        
                        progressBar.setSelection(progressBar.getMaximum());
                        progressBarLabel.setText("Browsing history analyzed.");
                        progressBarLabel.pack();
                        setPageComplete(true);
                        instructionLabel.setText("Click Next to continue.");
                        historyAnalyzed = true;
                    } else {
                        getShell().getDisplay().timerExec(1000, this);
                    }
                }
            });
        } catch (HistoryMinerException e) {
            log.error("Error extracting/analysing history", e);
            
            getHistoryMinerWizard().setCloseBrowserRequested(true);
            instructionLabel.setText(CLICK_NEXT);
            MessageDialog.openError(getShell(), "Error Analyzing Firefox History",
                    "An error occurred while extracting/analyzing Firefox history.  Please close all open Firefox windows and try again.\n\n" +
                    "Error details: " + e);

            setBackButtonEnabled(true);
            setPageComplete(true);
            progressBar.setSelection(0);
        }
    }
    
    private HistoryExtractor getMockHistoryExtractor() throws HistoryMinerException {
        return new HistoryExtractor() {
            @Override
            public List<HistoryVisit> extractHistory(Date startDate, Date endDate) throws HistoryMinerException {
                List<HistoryVisit> result = new ArrayList<HistoryVisit>();
                for (int i = 1; i <= 5; i++) {
                    result.add(new HistoryVisit(i, new Date(), VisitType.LINK, 1, i,
                            "www.site" + i + ".com", "Site Number " + i, "www.site" + (i - 1) + ".com"));
                }
                return result;
            }
        };
    }
    
    private HistoryClassifier getMockClassifier(List<HistoryVisit> visits) throws HistoryMinerException {
        return new HistoryClassifier(visits) {
            
            @Override
            protected void classifyLocation(Location location, boolean dumpFile) {
                location.locationType = LocationType.CODE_RELATED;
            }
        };
    }
    
}
