package ca.ubc.cs.reverb.eclipseplugin.views;

import java.awt.Desktop;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Text;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.wb.swt.ResourceManager;
import org.eclipse.swt.widgets.Link;

import ca.ubc.cs.reverb.eclipseplugin.PluginConfig;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;

public class UploadLogsDialog extends TitleAreaDialog implements SelectionListener {
    private final static String LOG_FILE_EXTENSION = ".txt";
    private final static String MORE_INFORMATION_URL = "http://code.google.com/p/reverb-plugin/";
    
    private PluginConfig config;
    private PluginLogger logger;

    private Text txtReverbIsReady;
    private Button btnViewLogs;
    private Link lnkMoreInformation;

    /**
     * Create the dialog.
     * @param parentShell
     */
    public UploadLogsDialog(Shell parentShell, PluginConfig config, PluginLogger logger) {
        super(parentShell);
        setHelpAvailable(false);
        this.config = config;
        this.logger = logger;
    }

    /**
     * Create contents of the dialog.
     * @param parent
     */
    @Override
    protected Control createDialogArea(Composite parent) {
        setTitleImage(ResourceManager.getPluginImage("ca.ubc.cs.reverb.eclipseplugin", "icons/reverb-48.png"));
        setTitle("Upload Reverb Usage Logs");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        txtReverbIsReady = new Text(container, SWT.WRAP);
        txtReverbIsReady.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
        txtReverbIsReady.setText("Reverb is ready to upload scrubbed usage logs.  Recommendations and browser page visits are identified in the logs by numbers only.  No code or web page details are included.  ");
        txtReverbIsReady.setBounds(10, 10, 424, 48);
        
        btnViewLogs = new Button(container, SWT.NONE);
        btnViewLogs.setBounds(10, 87, 75, 25);
        btnViewLogs.setText("View logs");
        btnViewLogs.addSelectionListener(this);
        
        lnkMoreInformation = new Link(container, SWT.NONE);
        lnkMoreInformation.setBounds(10, 64, 252, 15);
        lnkMoreInformation.setText("<a>More Information</a>");
        lnkMoreInformation.addSelectionListener(this);

        return area;
    }

    /**
     * Create contents of the button bar.
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        Button button_1 = createButton(parent, IDialogConstants.OK_ID, "Upload Now",
                true);
        button_1.setText("Upload now");
        Button button = createButton(parent, IDialogConstants.CANCEL_ID,
                IDialogConstants.CANCEL_LABEL, false);
        button.setText("Ask me later");
    }

    /**
     * Return the initial size of the dialog.
     */
    @Override
    protected Point getInitialSize() {
        return new Point(450, 300);
    }

    @Override
    public void widgetSelected(SelectionEvent event) {
        if (event.widget == lnkMoreInformation) {
            try {
                Desktop.getDesktop().browse(new URI(MORE_INFORMATION_URL));
            } catch (Exception e) {
                logger.logError("Failed to open browser on URL '" + MORE_INFORMATION_URL + "'", e);
            }
        } else if (event.widget == btnViewLogs) {
            
            DirectoryDialog dialog = new DirectoryDialog(getShell());
            dialog.setText("Choose folder for logs");

            String targetFolderPath = dialog.open();
            
            if (targetFolderPath != null) {
                File sourceFolder = new File(config.getStudyDataLogFolderPath());
                
                File[] sourceFiles = sourceFolder.listFiles(new FileFilter() {

                    @Override
                    public boolean accept(File file) {
                        if (file.getName().endsWith(LOG_FILE_EXTENSION) && file.length() > 0) {
                            return true;
                        }
                        return false;
                    }
                    
                });

                for (File sourceFile: sourceFiles) {
                    String sourceName = sourceFile.getName();
                    String sourceStem = sourceName.substring(0, 
                            sourceName.length() - LOG_FILE_EXTENSION.length());
                    String targetStem = sourceStem;
                    String targetFilePath = null;
                    int index = 1;
                    while (true) {
                        targetFilePath = targetFolderPath + File.separator + 
                                targetStem + LOG_FILE_EXTENSION;
                        if (new File(targetFilePath).exists()) {
                            targetStem = sourceStem + "-" + index++;
                        } else {
                            break;
                        }
                    } 
                    try {
                        copyFile(sourceFile, new File(targetFilePath));
                    } catch (IOException e) {
                        MessageDialog.openError(getShell(), "Error Copying File", 
                                "Error copying file '" + sourceFile.getAbsolutePath() + "' to '" +
                                targetFilePath + "'");
                        break;
                    }
                }
            }
        }
        
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
    }

    private static void copyFile(File sourceFile, File destFile) throws IOException {
        if(!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        }
        finally {
            if(source != null) {
                source.close();
            }
            if(destination != null) {
                destination.close();
            }
        }
    }
 }
