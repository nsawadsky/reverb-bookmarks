package ca.ubc.cs.reverb.eclipseplugin.views;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Button;

import ca.ubc.cs.reverb.eclipseplugin.PluginConfig;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;
import org.eclipse.swt.widgets.Label;

public class StudyCompleteDialog extends TrayDialog {
    private PluginConfig config;
    private PluginLogger logger;

    private Label lblReverbIsReady;

    /**
     * Create the dialog.
     * @param parentShell
     */
    public StudyCompleteDialog(Shell parentShell, PluginConfig config, PluginLogger logger) {
        super(parentShell);
        setHelpAvailable(false);
        this.config = config;
        this.logger = logger;
    }

    /**
     * Set dialog title.
     */
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Reverb User Study");
    }
    
    /**
     * Create contents of the dialog.
     * @param parent
     */
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        
        lblReverbIsReady = new Label(container, SWT.WRAP);
        lblReverbIsReady.setText("Thank-you for your participation in the Reverb user study!\r\n\r\nWhen you click OK, you will be taken to a web page where you can provide feedback on the tool.  We would love to hear your suggestions on how the tool can be improved.\r\n");
        lblReverbIsReady.setBounds(10, 10, 424, 48);
        lblReverbIsReady.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        return container;
    }

    /**
     * Create contents of the button bar.
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        Button button_1 = createButton(parent, IDialogConstants.OK_ID, "OK",
                true);
    }

    /**
     * Return the initial size of the dialog.
     */
    @Override
    protected Point getInitialSize() {
        return new Point(450, 300);
    }
 }
