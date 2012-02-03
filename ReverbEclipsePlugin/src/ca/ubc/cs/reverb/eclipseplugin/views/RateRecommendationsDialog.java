package ca.ubc.cs.reverb.eclipseplugin.views;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
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

import ca.ubc.cs.reverb.eclipseplugin.LocationAndRating;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;
import ca.ubc.cs.reverb.indexer.messages.Location;

import org.eclipse.wb.swt.ResourceManager;

public class RateRecommendationsDialog extends TitleAreaDialog {

    private Table table;
    private PluginLogger logger;
    private List<LocationAndRating> ratedLocations;
    
    /**
     * Create the dialog.
     * @param parentShell
     */
    public RateRecommendationsDialog(Shell parentShell, PluginLogger logger, List<Location> locations) {
        super(parentShell);
        setHelpAvailable(false);
        this.logger = logger;
        
        ratedLocations = new ArrayList<LocationAndRating>();
        for (Location location: locations) {
            ratedLocations.add(new LocationAndRating(location));
        }
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
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        ColumnViewerToolTipSupport.enableFor(viewer);
        
        TableViewerColumn titleViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        titleViewerColumn.setLabelProvider(new ColumnLabelProvider() {
            public String getText(Object item) {
                return ((LocationAndRating)item).location.title;
            }
        });
        
        TableColumn titleColumn = titleViewerColumn.getColumn();
        titleColumn.setResizable(true);
        titleColumn.setText("Page Title");
        titleColumn.setWidth(259);
        
        TableViewerColumn ratingViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        ratingViewerColumn.setLabelProvider(new ColumnLabelProvider() {
            public String getText(Object item) {
                int rating = ((LocationAndRating)item).rating; 
                if (rating == 0) {
                    return "";
                }
                return Integer.toString(rating);
            }
        });
        ratingViewerColumn.setEditingSupport(new RatingEditingSupport(viewer));

        TableColumn ratingColumn = ratingViewerColumn.getColumn();
        ratingColumn.setResizable(true);
        ratingColumn.setText("Rating");
        ratingColumn.setWidth(70);

        TableViewerColumn commentViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        commentViewerColumn.setLabelProvider(new ColumnLabelProvider() {
            public String getText(Object item) {
                return ((LocationAndRating)item).comment;
            }
        });
        commentViewerColumn.setEditingSupport(new CommentEditingSupport(viewer));
        
        TableColumn commentColumn = commentViewerColumn.getColumn();
        commentColumn.setResizable(true);
        commentColumn.setText("Comment");
        commentColumn.setWidth(268);
        
        viewer.setInput(this.ratedLocations);
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
    
    private class CommentEditingSupport extends EditingSupport {
        private TableViewer viewer;
        
        public CommentEditingSupport(TableViewer viewer) {
            super(viewer);
            this.viewer = viewer;
        }
        
        @Override
        protected CellEditor getCellEditor(Object element) {
            return new TextCellEditor(viewer.getTable());
        }

        @Override
        protected boolean canEdit(Object element) {
            return true;
        }

        @Override
        protected Object getValue(Object element) {
            return ((LocationAndRating)element).comment;
        }

        @Override
        protected void setValue(Object element, Object value) {
            ((LocationAndRating)element).comment = (value == null ? null : value.toString());
            viewer.refresh();
        }
        
    }

    private class RatingEditingSupport extends EditingSupport {
        private TableViewer viewer;
        
        public RatingEditingSupport(TableViewer viewer) {
            super(viewer);
            this.viewer = viewer;
        }
        
        @Override
        protected CellEditor getCellEditor(Object element) {
            ComboBoxCellEditor result = new ComboBoxCellEditor(viewer.getTable(), 
                    new String[] {"5", "4", "3", "2", "1"}, SWT.READ_ONLY);
            result.setActivationStyle(ComboBoxCellEditor.DROP_DOWN_ON_MOUSE_ACTIVATION);
            return result;
        }

        @Override
        protected boolean canEdit(Object element) {
            return true;
        }

        @Override
        protected Object getValue(Object element) {
            return ((LocationAndRating)element).rating - 1; 
        }

        @Override
        protected void setValue(Object element, Object value) {
            ((LocationAndRating)element).rating = ((Integer)value)+1;
            viewer.refresh();
        }
        
    }
}
