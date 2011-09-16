package ca.ubc.cs.hminer.study.ui;

import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.spi.RootLogger;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import ca.ubc.cs.hminer.study.core.Util;

public class HistoryMinerShell extends Shell {

    /**
     * Launch the application.
     * @param args
     */
    public static void main(String args[]) {
        BasicConfigurator.configure();
        RootLogger.getRootLogger().setLevel(Level.WARN);

        boolean testMode = false;
        boolean indexMode = false;
        Map<String, String> parsedArgs = Util.parseArgs(args);
        String mode = parsedArgs.get("mode");
        if (mode != null) {
            if (mode.equals("test")) {
                testMode = true;
            } else if (mode.equals("index")) {
                indexMode = true;
            }
        }
        
        Display display = Display.getDefault();
        HistoryMinerShell shell = new HistoryMinerShell(display);
        
        int majorVersion = 0;
        int minorVersion = 0;
        try {
            String javaVersion = System.getProperty("java.version");
            String[] elements = javaVersion.split("\\.");
            majorVersion = Integer.parseInt(elements[0]);
            minorVersion = Integer.parseInt(elements[1]);
        } catch (Exception e) {}
        
        if (majorVersion < 1 || minorVersion < 6) {
            MessageDialog.openError(shell, "Unsupported Java Version", 
                    "Java 1.6 or later required.  Please install the latest version from www.java.com.");
        } else {
        
            HistoryMinerWizardDialog dialog = new HistoryMinerWizardDialog(shell, new HistoryMinerWizard(testMode, indexMode));
            dialog.setBlockOnOpen(true);
            dialog.open();
        }
        
        Display.getDefault().dispose();
    }

    /**
     * Create the shell.
     * @param display
     */
    public HistoryMinerShell(Display display) {
        super(display, SWT.SHELL_TRIM);
        createContents();
    }

    /**
     * Create contents of the shell.
     */
    protected void createContents() {
        setText("Browsing History Analyzer");
        setSize(450, 300);

    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

}
