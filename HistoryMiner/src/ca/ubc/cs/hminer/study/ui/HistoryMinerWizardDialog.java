package ca.ubc.cs.hminer.study.ui;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

public class HistoryMinerWizardDialog extends WizardDialog {

    public HistoryMinerWizardDialog(Shell parentShell, IWizard newWizard) {
        super(parentShell, newWizard);
    }
    
    @Override 
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        Button finishButton = getButton(IDialogConstants.FINISH_ID);
        finishButton.setText("Submit");
    }
    
    @Override 
    protected void nextPressed() {
        HistoryMinerWizard wizard = (HistoryMinerWizard)this.getWizard();
        if (wizard.handleNextPressed()) {
            super.nextPressed();
        }
    }

    
}
