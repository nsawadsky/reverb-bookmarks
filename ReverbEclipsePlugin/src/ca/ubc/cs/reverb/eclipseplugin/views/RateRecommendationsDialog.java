package ca.ubc.cs.reverb.eclipseplugin.views;

import java.awt.Desktop;
import java.net.URI;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;
import org.eclipse.wb.swt.ResourceManager;

public class RateRecommendationsDialog extends TitleAreaDialog {

    private Table table;
    private PluginLogger logger;
    
    /**
     * Create the dialog.
     * @param parentShell
     */
    public RateRecommendationsDialog(Shell parentShell, PluginLogger logger) {
        super(parentShell);
        setHelpAvailable(false);
        this.logger = logger;
    }

    /**
     * Create contents of the dialog.
     * @param parent
     */
    @Override
    protected Control createDialogArea(Composite parent) {
        setMessage("Did you find the following Reverb links useful?  Please rate each one, and let us know any comments you have.  (You can double-click on a row to open the page in your browser.)");
        setTitleImage(ResourceManager.getPluginImage("ca.ubc.cs.reverb.eclipseplugin", "icons/reverb-48.png"));
        setTitle("Rate Recommendations");
        
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NULL);

        container.setLayout(new GridLayout(1, false));
        
        table = new Table(container, SWT.BORDER | SWT.FULL_SELECTION);
        GridData gd_table = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd_table.widthHint = 591;
        gd_table.heightHint = 293;
        table.setLayoutData(gd_table);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.addSelectionListener(new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
                TableItem item = (TableItem)event.item;
                String url = item.getText(0);
                if (url != null) {
                    try {
                        Desktop.getDesktop().browse(new URI(url));
                    } catch (Exception e) {
                        logger.logError("Exception opening browser on URL " + url, e);
                    }
                }
            }
            
        });
                
        TableViewer viewer = new TableViewer(table);
        ColumnViewerToolTipSupport.enableFor(viewer);
        
        CellLabelProvider labelProvider = new CellLabelProvider() {

            public void update(ViewerCell cell) {
            }
        };

        TableViewerColumn titleViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        titleViewerColumn.setLabelProvider(labelProvider);
        
        TableColumn titleColumn = titleViewerColumn.getColumn();
        titleColumn.setResizable(true);
        titleColumn.setText("Page Title");
        titleColumn.setWidth(259);
        
        TableViewerColumn commentsViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        commentsViewerColumn.setLabelProvider(labelProvider);

        TableColumn commentsColumn = commentsViewerColumn.getColumn();
        commentsColumn.setResizable(true);
        commentsColumn.setText("Comments");
        commentsColumn.setWidth(291);
        
        CellEditor[] editors = new CellEditor[3];
        
        TextCellEditor textEditor = new TextCellEditor(table);
        editors[1] = textEditor;
        
        TableViewerColumn ratingViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        ratingViewerColumn.setLabelProvider(labelProvider);

        TableColumn ratingColumn = ratingViewerColumn.getColumn();
        ratingColumn.setResizable(true);
        ratingColumn.setText("Rating");
        ratingColumn.setWidth(47);

        ComboBoxCellEditor comboBoxEditor = new ComboBoxCellEditor(table,
                new String[]{"1", "2", "3", "4", "5"}, SWT.READ_ONLY);
        editors[2] = comboBoxEditor;
        
        viewer.setCellEditors(editors);
        
        return area;
    }

    /**
     * Create contents of the button bar.
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
                true);
        createButton(parent, IDialogConstants.CANCEL_ID,
                IDialogConstants.CANCEL_LABEL, false);
    }

    /**
     * Return the initial size of the dialog.
     */
    @Override
    protected Point getInitialSize() {
        return new Point(630, 524);
    }
}
