package ca.ubc.cs.hminer.study.ui;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;

import ca.ubc.cs.hminer.study.core.HistoryMinerData;

import org.eclipse.swt.widgets.Text;

public class StartEndDatePage extends HistoryMinerWizardPage implements SelectionListener, KeyListener {
    private final static Logger log = Logger.getLogger(StartEndDatePage.class);
    
    private Calendar earliestVisitCal;
    private Calendar startDateCal;
    private Calendar endDateCal;
    private DateTime startDateControl;
    private DateTime endDateControl;
    private Label startDateLabel;
    private Label endDateLabel;
    
    private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy");
    private Composite composite;
    private Label lblPrimaryProgrammingLanguage;
    private Text primaryProgrammingLanguageText;
    private boolean datesAreOkay = true;
    
    /**
     * Create the wizard.
     */
    public StartEndDatePage() {
        super("wizardPage");
        setTitle("Choose Start/End Date");
        setDescription("");
        setPageComplete(false);
    }

    /**
     * Create contents of the wizard.
     * @param parent
     */
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        container.setLayout(new GridLayout(2, false));
        
        Label lblNewLabel = new Label(container, SWT.WRAP);
        GridData gd_lblNewLabel = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
        gd_lblNewLabel.widthHint = 565;
        gd_lblNewLabel.heightHint = 48;
        lblNewLabel.setLayoutData(gd_lblNewLabel);
        lblNewLabel.setText("Choose the start and end dates for which browsing history will be analyzed, or use the defaults.  " + 
                            "A window of at least two months that includes periods of active coding is preferred.");
        
        startDateLabel = new Label(container, SWT.NONE);
        startDateLabel.setAlignment(SWT.CENTER);
        GridData gd_startDateLabel = new GridData(SWT.CENTER, SWT.TOP, false, false, 1, 1);
        gd_startDateLabel.widthHint = 150;
        startDateLabel.setLayoutData(gd_startDateLabel);
        
        endDateLabel = new Label(container, SWT.NONE);
        endDateLabel.setAlignment(SWT.CENTER);
        GridData gd_endDateLabel = new GridData(SWT.CENTER, SWT.TOP, false, false, 1, 1);
        gd_endDateLabel.widthHint = 150;
        endDateLabel.setLayoutData(gd_endDateLabel);
        
        startDateControl = new DateTime(container, SWT.CALENDAR);
        startDateControl.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false, 1, 1));
        
        startDateControl.addSelectionListener(this);
        
        endDateControl = new DateTime(container, SWT.CALENDAR);
        endDateControl.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));

        endDateControl.addSelectionListener(this);
        
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        
        composite = new Composite(container, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        composite.setLayout(new GridLayout(2, false));
        
        lblPrimaryProgrammingLanguage = new Label(composite, SWT.NONE);
        lblPrimaryProgrammingLanguage.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblPrimaryProgrammingLanguage.setText("Primary programming language during this period:");
        
        primaryProgrammingLanguageText = new Text(composite, SWT.BORDER);
        GridData gd_text = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gd_text.widthHint = 180;
        primaryProgrammingLanguageText.setLayoutData(gd_text);
        primaryProgrammingLanguageText.addKeyListener(this);
        
        endDateCal = getDateCalendar();
        startDateCal = getDateCalendar();
        startDateCal.add(Calendar.MONTH, -3);
        
        updateDateControl(startDateControl, startDateCal);
        updateDateControl(endDateControl, endDateCal);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        updateStartDateLabel(startDateCal.getTime());
        updateEndDateLabel(endDateCal.getTime());
    }

    @Override
    public void widgetSelected(SelectionEvent e) {
        handleSelectionEvent(e);
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
        handleSelectionEvent(e);
    }
    
    @Override
    public void keyPressed(KeyEvent arg0) {
        checkPageComplete();
    }

    @Override
    public void keyReleased(KeyEvent arg0) {
        checkPageComplete();
    }

    private void handleSelectionEvent(SelectionEvent event) {
        updateCalendarInstance(startDateCal, startDateControl);
        updateCalendarInstance(endDateCal, endDateControl);

        updateStartDateLabel(startDateCal.getTime());
        updateEndDateLabel(endDateCal.getTime());
        
        datesAreOkay = false;
        
        if (startDateCal.before(earliestVisitCal)) {
            this.setErrorMessage("Start date cannot be before the beginning of your browser history (" + DATE_FORMAT.format(earliestVisitCal.getTime()) + ").");
            this.setPageComplete(false);
            return;
        }
        if (endDateCal.before(startDateCal)) {
            this.setErrorMessage("Start date cannot be after end date.");
            setPageComplete(false);
            return;
        }
        Calendar now = Calendar.getInstance();
        if (endDateCal.after(now)) {
            this.setErrorMessage("End date cannot be after today.");
            setPageComplete(false);
            return;
        }

        this.setErrorMessage(null);
        datesAreOkay = true;
        checkPageComplete();
    }
    
    @Override 
    protected void onPageOpening() {
        earliestVisitCal = getDateCalendarFromTime(getHistoryMinerWizard().getEarliestVisitDate());

        if (startDateCal.before(earliestVisitCal)) {
            startDateCal.setTime(earliestVisitCal.getTime());
        }
        
        updateDateControl(startDateControl, startDateCal);

        updateStartDateLabel(startDateCal.getTime());
    }

    @Override
    protected void onPageClosing() {
        // Store current values in wizard.
        HistoryMinerData data = getHistoryMinerData();
        data.historyStartDate = startDateCal.getTime();

        Calendar tempEndDateCal = Calendar.getInstance();
        tempEndDateCal.setTime(endDateCal.getTime());
        tempEndDateCal.add(Calendar.DAY_OF_MONTH, 1);
        data.historyEndDate = tempEndDateCal.getTime();
        
        data.participantPrimaryProgrammingLanguage = primaryProgrammingLanguageText.getText().trim();
        
        log.info("Start date = " + startDateCal.getTime());
        log.info("End date = " + tempEndDateCal.getTime());
    }
    
    private void checkPageComplete() {
        setPageComplete(datesAreOkay && !primaryProgrammingLanguageText.getText().trim().isEmpty());
    }
    
    private void updateStartDateLabel(Date startDate) {
        startDateLabel.setText("Start Date: " + DATE_FORMAT.format(startDate));
    }
    
    private void updateEndDateLabel(Date endDate) {
        endDateLabel.setText("End Date: " + DATE_FORMAT.format(endDate));
    }
    
    private void updateDateControl(DateTime control, Calendar date) {
        control.setDate(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
    }
    
    private void updateCalendarInstance(Calendar instance, DateTime control) {
        instance.clear();
        instance.set(control.getYear(), control.getMonth(), control.getDay());
    }
    
    private Calendar getDateCalendar() {
        return getDateCalendarFromTime(new Date());
    }
    
    private Calendar getDateCalendarFromTime(Date time) {
        Calendar tempCal = Calendar.getInstance();
        tempCal.setTime(time);
        Calendar result = Calendar.getInstance();
        result.clear();
        result.set(tempCal.get(Calendar.YEAR), tempCal.get(Calendar.MONTH), tempCal.get(Calendar.DAY_OF_MONTH));
        return result;
    }

}
