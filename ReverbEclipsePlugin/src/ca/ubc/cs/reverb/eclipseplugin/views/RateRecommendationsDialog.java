package ca.ubc.cs.reverb.eclipseplugin.views;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import ca.ubc.cs.reverb.eclipseplugin.LocationAndRating;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;
import ca.ubc.cs.reverb.indexer.messages.Location;

import org.eclipse.swt.widgets.Text;
import org.eclipse.wb.swt.SWTResourceManager;

public class RateRecommendationsDialog extends TrayDialog {

    private Table table;
    private PluginLogger logger;
    private List<LocationAndRating> ratedLocations;
    private Text txtDidYouFind;
    
    /**
     * Create the dialog.
     * @param parentShell
     */
    public RateRecommendationsDialog(Shell parentShell, PluginLogger logger, List<Location> locations) {
        super(parentShell);
        setShellStyle(this.getShellStyle() | SWT.RESIZE);
        setHelpAvailable(false);
        this.logger = logger;
        
        ratedLocations = new ArrayList<LocationAndRating>();
        for (Location location: locations) {
            ratedLocations.add(new LocationAndRating(location));
        }
    }

    /**
     * Set dialog title.
     */
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Rate Recommendations");
    }
    
    /**
     * Create contents of the dialog.
     * @param parent
     */
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        
        txtDidYouFind = new Text(container, SWT.WRAP);
        txtDidYouFind.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
        txtDidYouFind.setText("You clicked on the following links.  Did you find them useful?  Please rate each one (5 most useful, 1 least useful), and let us know any comments you have.  You can double-click on a row to open the page in your browser.");
        txtDidYouFind.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        
        table = new Table(container, SWT.BORDER | SWT.FULL_SELECTION);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.addSelectionListener(new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
                TableItem item = (TableItem)event.item;
                LocationAndRating locationAndRating = (LocationAndRating)item.getData();
                if (locationAndRating != null) {
                    try {
                        Desktop.getDesktop().browse(new URI(locationAndRating.location.url));
                    } catch (Exception e) {
                        logger.logError("Exception opening browser on URL " + locationAndRating.location.url, e);
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
            
            public String getToolTipText(Object item) {
                return ((LocationAndRating)item).location.url;
            }
        });
        
        TableColumn titleColumn = titleViewerColumn.getColumn();
        titleColumn.setResizable(true);
        titleColumn.setText("Page Title");
        titleColumn.setWidth(230);
        
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
        commentColumn.setWidth(288);
        
        viewer.setInput(this.ratedLocations);
        return container;
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
            String comment = ((LocationAndRating)element).comment; 
            return (comment == null ? "" : comment);
        }

        @Override
        protected void setValue(Object element, Object value) {
            ((LocationAndRating)element).comment = (value == null ? null : value.toString());
            viewer.refresh();
        }
        
    }

    private class RatingEditingSupport extends EditingSupport {
        private TableViewer viewer;
        private final List<String> labels = Arrays.asList("", "5", "4", "3", "2", "1");
        
        public RatingEditingSupport(TableViewer viewer) {
            super(viewer);
            this.viewer = viewer;
        }
        
        @Override
        protected CellEditor getCellEditor(Object element) {
            ComboBoxCellEditor result = new ComboBoxCellEditor(viewer.getTable(), labels.toArray(new String[] {}), SWT.READ_ONLY);
            result.setActivationStyle(ComboBoxCellEditor.DROP_DOWN_ON_MOUSE_ACTIVATION);
            return result;
        }

        @Override
        protected boolean canEdit(Object element) {
            return true;
        }

        @Override
        protected Object getValue(Object element) {
            int rating = ((LocationAndRating)element).rating;
            if (rating == 0) {
                return 0;
            }
            return labels.indexOf(Integer.toString(rating));
        }

        @Override
        protected void setValue(Object element, Object value) {
            LocationAndRating locationAndRating = (LocationAndRating)element;
            int intValue = (Integer)value;
            if (intValue == 0) {
                locationAndRating.rating = 0;
            } else {
                locationAndRating.rating = Integer.parseInt(labels.get(intValue));
            }
            viewer.refresh();
        }
        
    }
}
