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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import ca.ubc.cs.reverb.eclipseplugin.PluginConfig;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;
import ca.ubc.cs.reverb.eclipseplugin.reports.LocationRating;

public class RateRecommendationsDialog extends TrayDialog {
    private Table table;
    private PluginLogger logger;
    private PluginConfig config;
    private List<LocationRating> locationRatings;
    private Label lblDidYouFind;
    
    /**
     * Create the dialog.
     * @param parentShell
     */
    public RateRecommendationsDialog(Shell parentShell, PluginConfig config, PluginLogger logger, List<LocationRating> locationRatings) {
        super(parentShell);
        setShellStyle(this.getShellStyle() | SWT.RESIZE);
        setHelpAvailable(false);
        this.config = config;
        this.logger = logger;
        
        this.locationRatings = new ArrayList<LocationRating>(locationRatings);
    }

    public List<LocationRating> getLocationRatings() {
        return locationRatings;
    }
    
    /**
     * Set dialog title.
     */
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Rate Reverb Recommendations");
    }
    
    /**
     * Create contents of the dialog.
     * @param parent
     */
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        
        lblDidYouFind = new Label(container, SWT.WRAP);
        lblDidYouFind.setText("You clicked on the links below.  Did you find them useful?  Please rate " +
                "each one (5 most useful, 1 least useful), and let us know any comments you have.  You can " +
                "double-click on the title to open a page in your browser.");
        lblDidYouFind.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        
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
                LocationRating locationRating = (LocationRating)item.getData();
                if (locationRating != null) {
                    try {
                        Desktop.getDesktop().browse(new URI(locationRating.url));
                    } catch (Exception e) {
                        logger.logError("Exception opening browser on URL " + locationRating.url, e);
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
                return ((LocationRating)item).title;
            }
            
            public String getToolTipText(Object item) {
                return ((LocationRating)item).url;
            }
        });
        
        TableColumn titleColumn = titleViewerColumn.getColumn();
        titleColumn.setResizable(true);
        titleColumn.setText("Page Title");
        titleColumn.setWidth(230);
        
        TableViewerColumn ratingViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        ratingViewerColumn.setLabelProvider(new ColumnLabelProvider() {
            public String getText(Object item) {
                int rating = ((LocationRating)item).rating; 
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
                return ((LocationRating)item).comment;
            }
        });
        commentViewerColumn.setEditingSupport(new CommentEditingSupport(viewer));
        
        TableColumn commentColumn = commentViewerColumn.getColumn();
        commentColumn.setResizable(true);
        commentColumn.setText("Comment");
        commentColumn.setWidth(324);
        
        viewer.setInput(locationRatings);
        return container;
    }

    /**
     * Create contents of the button bar.
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Submit", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "Skip", false);
    }

    /**
     * Return the initial size of the dialog.
     */
    @Override
    protected Point getInitialSize() {
        return new Point(667, 446);
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
            String comment = ((LocationRating)element).comment; 
            return (comment == null ? "" : comment);
        }

        @Override
        protected void setValue(Object element, Object value) {
            ((LocationRating)element).comment = (value == null ? null : value.toString());
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
            int rating = ((LocationRating)element).rating;
            if (rating == 0) {
                return 0;
            }
            return labels.indexOf(Integer.toString(rating));
        }

        @Override
        protected void setValue(Object element, Object value) {
            LocationRating locationRating = (LocationRating)element;
            int intValue = (Integer)value;
            if (intValue == 0) {
                locationRating.rating = 0;
            } else {
                locationRating.rating = Integer.parseInt(labels.get(intValue));
            }
            viewer.refresh();
        }
        
    }
}
