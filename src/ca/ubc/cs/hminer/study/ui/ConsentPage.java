package ca.ubc.cs.hminer.study.ui;

import java.util.Date;

import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import ca.ubc.cs.hminer.study.core.HistoryExtractor;
import ca.ubc.cs.hminer.study.core.Util.RunnableWithResult;

public class ConsentPage extends HistoryMinerWizardPage {

    private final static Logger log = Logger.getLogger(ConsentPage.class);
    
    /**
     * Create the wizard.
     */
    public ConsentPage() {
        super("wizardPage");
        setTitle("Consent");
        setDescription("");
    }

    /**
     * Create contents of the wizard.
     * @param parent
     */
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        
        Label lblNewLabel = new Label(container, SWT.NONE);
        lblNewLabel.setBounds(10, 10, 554, 262);
        lblNewLabel.setText("Consent form goes here ...");
    }

    @Override 
    public boolean onNextPressed() {
        RunnableWithResult<Date> earliestVisitDateExtractor = new RunnableWithResult<Date>(){
            public Date call() throws Exception {
                return getHistoryMinerWizard().getHistoryExtractor().getEarliestVisitDate();
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
    
}
