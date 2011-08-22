package ca.ubc.cs.hminer.study.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;

import ca.ubc.cs.hminer.study.core.Util.RunnableWithResult;

public class SubmitPage extends HistoryMinerWizardPage implements SelectionListener {
    private Button btnAnonymizePartial;
    private Button btnAnonymizeAll;
    private Button btnViewReport;
    
    /**
     * Create the wizard.
     */
    public SubmitPage() {
        super("wizardPage");
        setTitle("Submit Report");
        setDescription("");
    }

    /**
     * Create contents of the wizard.
     * @param parent
     */
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        container.setLayout(new GridLayout(1, false));
        
        Label lblNewLabel = new Label(container, SWT.WRAP);
        GridData gd_lblNewLabel = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
        gd_lblNewLabel.widthHint = 565;
        lblNewLabel.setLayoutData(gd_lblNewLabel);
        lblNewLabel.setText("Non-development-related pages will be fully anonymized in the report that is submitted.  You can choose whether to include URL's for development-related pages.  Choosing to include URL's for these pages will help researchers gain a better understanding of developer web-browsing patterns.");
        
        btnAnonymizePartial = new Button(container, SWT.RADIO);
        btnAnonymizePartial.setText("Include URL's for just the pages flagged on the previous screen as development-related.");
        btnAnonymizePartial.setSelection(getHistoryMinerData().anonymizePartial);
        btnAnonymizePartial.addSelectionListener(this);
        
        btnAnonymizeAll = new Button(container, SWT.RADIO);
        btnAnonymizeAll.setText("Do not include any URL's.");
        btnAnonymizeAll.setSelection(!getHistoryMinerData().anonymizePartial);
        btnAnonymizeAll.addSelectionListener(this);
        
        new Label(container, SWT.NONE);
        
        btnViewReport = new Button(container, SWT.NONE);
        btnViewReport.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, false, true, 1, 1));
        btnViewReport.setText("View Report");
        btnViewReport.addSelectionListener(this);
    }
    
    @Override 
    protected void onPageOpened() {
        getHistoryMinerWizard().setSubmitButtonEnabled(true);
    }

    @Override 
    protected void onPageClosing() {
        getHistoryMinerWizard().setSubmitButtonEnabled(false);
    }
    
    @Override
    public void widgetSelected(SelectionEvent event) {
        if (event.widget == btnAnonymizePartial || event.widget == btnAnonymizeAll) {
            getHistoryMinerData().anonymizePartial = btnAnonymizePartial.getSelection();
        } else if (event.widget == btnViewReport) {
            
            RunnableWithResult<String> reportGenerator = new RunnableWithResult<String>(){
                public String call() throws Exception {
                    return getHistoryMinerWizard().generateReport();
                }
            };
            
            BusyIndicator.showWhile(getShell().getDisplay(), reportGenerator);
            if (reportGenerator.getError() != null) {
                MessageDialog.openError(getShell(), "Error Generating Report", 
                        "Error generating report: " + reportGenerator.getError());
                return;
            }
            
            FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
            dialog.setText("Save Report");
            dialog.setFilterExtensions(new String[] {"*.txt"});
            dialog.setFileName("BrowsingReport.txt");

            File file = null;
            boolean cancelled = false;
            while (! cancelled && file == null) {
                String filePath = dialog.open();
                if (filePath == null) { 
                    cancelled = true;
                } else {
                    File tempFile = new File(filePath); 
                    if (!tempFile.exists() || 
                            MessageDialog.openQuestion(getShell(), "Save Report", "Overwrite " + tempFile.getAbsolutePath() + "?") ) {
                        file = tempFile;
                    } 
                }
            }
            if (file != null ) {
                try {
                    FileWriter writer = null;
                    try {
                        writer = new FileWriter(file);
                        writer.write(reportGenerator.getResult());
                    } finally {
                        if (writer != null) { writer.close(); }
                    }
                    Desktop.getDesktop().open(file);
                } catch (Exception e) {
                    MessageDialog.openError(getShell(), "Error Saving/Opening Report", 
                            "Error saving/opening report: " + e);
                }
            }
        }
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
    }
    
    
}
