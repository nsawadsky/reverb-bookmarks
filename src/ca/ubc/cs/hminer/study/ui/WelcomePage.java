package ca.ubc.cs.hminer.study.ui;

import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Text;

import ca.ubc.cs.hminer.study.core.HistoryExtractor;
import ca.ubc.cs.hminer.study.core.Util.RunnableWithResult;
import ca.ubc.cs.hminer.study.core.WebBrowserType;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.layout.FillLayout;

public class WelcomePage extends HistoryMinerWizardPage implements KeyListener, SelectionListener {
    private final static Logger log = Logger.getLogger(HistoryMinerWizardPage.class);

    private Text participantIdText;
    private Text occupationText;
    private Button firefoxRadioButton;
    private Button chromeRadioButton;
    private Button chromiumRadioButton;
    private WebBrowserType webBrowserType = WebBrowserType.MOZILLA_FIREFOX;
    
    private final static Pattern UUID_PATTERN = Pattern.compile(
            "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");

    private final static String TEST_PARTICIPANT_ID = "07cda549-4d2a-4f43-9653-7a9221615a03";
    
    /**
     * Create the wizard.
     */
    public WelcomePage() {
        super("wizardPage");
        setTitle("Welcome");
        setDescription("");
        this.setPageComplete(false);
    }

    /**
     * Create contents of the wizard.
     * @param parent
     */
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        container.setLayout(new GridLayout(3, false));
        
        Label lblNewLabel = new Label(container, SWT.WRAP);
        GridData gd_lblNewLabel = new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1);
        gd_lblNewLabel.heightHint = 76;
        gd_lblNewLabel.widthHint = 564;
        lblNewLabel.setLayoutData(gd_lblNewLabel);
        lblNewLabel.setText("Welcome to the developer browsing history analyzer.  This tool will analyze a portion of your browsing history to see how often you revisit development-related web pages as you work.  No data will be submitted without your direct consent.  You will have the opportunity to review all data collected before choosing whether to submit it.");
        
        Label lblParticipantId = new Label(container, SWT.NONE);
        lblParticipantId.setText("Participant ID:");
        
        participantIdText = new Text(container, SWT.BORDER);
        GridData gd_text_2 = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_text_2.widthHint = 231;
        participantIdText.setLayoutData(gd_text_2);
        
        participantIdText.addKeyListener(this);
        
        if (getHistoryMinerWizard().isTestMode()) {
            participantIdText.setText(TEST_PARTICIPANT_ID);
        }
        
        Label lblNewLabel_1 = new Label(container, SWT.NONE);
        GridData gd_lblNewLabel_1 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_lblNewLabel_1.widthHint = 216;
        lblNewLabel_1.setLayoutData(gd_lblNewLabel_1);
        
        Label lblPleaseEnterYour = new Label(container, SWT.NONE);
        lblPleaseEnterYour.setText("Occupation:");
        
        occupationText = new Text(container, SWT.BORDER);
        GridData gd_text = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gd_text.widthHint = 310;
        occupationText.setLayoutData(gd_text);
        
        occupationText.addKeyListener(this);
        new Label(container, SWT.NONE);
        
        Label lblPrimaryWebBrowser = new Label(container, SWT.NONE);
        lblPrimaryWebBrowser.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblPrimaryWebBrowser.setText("Primary Web Browser: ");
        
        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        FillLayout fl_composite = new FillLayout(SWT.HORIZONTAL);
        fl_composite.spacing = 10;
        composite.setLayout(fl_composite);
        
        firefoxRadioButton = new Button(composite, SWT.RADIO);
        firefoxRadioButton.setText("Mozilla Firefox");
        
        chromeRadioButton = new Button(composite, SWT.RADIO);
        chromeRadioButton.setText("Google Chrome");
        
        chromiumRadioButton = new Button(composite, SWT.RADIO);
        chromiumRadioButton.setText("Chromium");
        
        chromeRadioButton.addSelectionListener(this);
        firefoxRadioButton.addSelectionListener(this);
        chromiumRadioButton.addSelectionListener(this);
        new Label(container, SWT.NONE);
        
        Label lblNewLabel_2 = new Label(container, SWT.NONE);
        lblNewLabel_2.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true, 1, 1));
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        checkPageComplete();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        checkPageComplete();
    }
    
    @Override 
    public boolean onNextPressed() {
        RunnableWithResult<Date> earliestVisitDateExtractor = new RunnableWithResult<Date>(){
            public Date call() throws Exception {
                return HistoryExtractor.getHistoryExtractor(webBrowserType).getEarliestVisitDate();
            }
        };
        
        BusyIndicator.showWhile(getShell().getDisplay(), earliestVisitDateExtractor);
        if (earliestVisitDateExtractor.getError() != null) {
            log.error("Error extracting earliest visit date", earliestVisitDateExtractor.getError());
            getHistoryMinerWizard().setCloseBrowserRequested(true);
            MessageDialog.openError(getShell(), "Error Accessing Browser History",
                    "An error occurred while trying to access browser history.  Please close all open browser windows and try again.\n\n" +
                    "Error details: " + earliestVisitDateExtractor.getError());
            return false;
        }

        getHistoryMinerWizard().setEarliestVisitDate(earliestVisitDateExtractor.getResult());
        log.info("Earliest visit date = " + earliestVisitDateExtractor.getResult());

        return true;
    }
    
    @Override
    protected void onPageClosing() {
        getHistoryMinerData().participantId = UUID.fromString(participantIdText.getText().trim());
        getHistoryMinerData().participantOccupation = occupationText.getText().trim();
        getHistoryMinerData().participantPrimaryWebBrowser = webBrowserType;
    }
    
    private void checkPageComplete() {
        boolean pageComplete = false;
        if (firefoxRadioButton.getSelection() || chromeRadioButton.getSelection() || chromiumRadioButton.getSelection()) {
            if (!occupationText.getText().trim().isEmpty()) {
                try {
                    String participantId = participantIdText.getText().trim();
                    UUID.fromString(participantId);
                    Matcher matcher = UUID_PATTERN.matcher(participantId);
                    if (matcher.find()) {
                        pageComplete = true;
                    }
                } catch (IllegalArgumentException e) {}
            }
        }
        setPageComplete(pageComplete);
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent event) {
        widgetSelected(event);
    }

    @Override
    public void widgetSelected(SelectionEvent event) {
        if (event.widget == firefoxRadioButton || event.widget == chromeRadioButton || event.widget == chromiumRadioButton) {
            webBrowserType = WebBrowserType.MOZILLA_FIREFOX;
            if (chromeRadioButton.getSelection()) {
                webBrowserType = WebBrowserType.GOOGLE_CHROME;
            } else if (chromiumRadioButton.getSelection()) {
                webBrowserType = WebBrowserType.CHROMIUM;
            }
            checkPageComplete();
        }
    }
    
}
