package ca.ubc.cs.hminer.study.ui;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;

import ca.ubc.cs.hminer.study.core.HistoryMinerData;

public abstract class HistoryMinerWizardPage extends WizardPage {
    private boolean backButtonEnabled = true;

    public HistoryMinerWizardPage(String pageName) {
        super(pageName);
    }

    public void setBackButtonEnabled(boolean enabled) {
        backButtonEnabled = enabled;
        getContainer().updateButtons();
    }
    
    @Override
    public IWizardPage getPreviousPage() {
        if (!backButtonEnabled) {
            return null;
        }
        return super.getPreviousPage();
    }
    
    public HistoryMinerWizard getHistoryMinerWizard() {
        return (HistoryMinerWizard)getWizard();
    }
    
    public HistoryMinerData getHistoryMinerData() {
        return getHistoryMinerWizard().getHistoryMinerData();
    }
    
    protected boolean onNextPressed() {
        return true;
    }

    protected void onPageOpening() {
        
    }
    
    protected void onPageOpened() {
        
    }
    
    protected void onPageClosing() {
    }
    
}
