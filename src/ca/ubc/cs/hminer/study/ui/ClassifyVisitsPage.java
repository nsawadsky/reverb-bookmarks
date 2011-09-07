package ca.ubc.cs.hminer.study.ui;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


import org.apache.log4j.Logger;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import ca.ubc.cs.hminer.study.core.ClassifierData;
import ca.ubc.cs.hminer.study.core.Location;
import ca.ubc.cs.hminer.study.core.LocationAndClassification;
import ca.ubc.cs.hminer.study.core.LocationAndVisits;
import ca.ubc.cs.hminer.study.core.LocationType;

import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.GridData;

public class ClassifyVisitsPage extends HistoryMinerWizardPage {
    private final static Logger log = Logger.getLogger(ClassifyVisitsPage.class);
    
    private final static int LOCATIONS_TO_CLASSIFY_COUNT = 25;
    
    private Table table;
    
    private boolean hasBeenOpened = false;
    
    private List<Location> locationsToClassify;
    
    /**
     * Create the wizard.
     */
    public ClassifyVisitsPage() {
        super("wizardPage");
        setTitle("Classify Page Visits");
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
        
        Label lblUncheckTheBox = new Label(container, SWT.WRAP);
        GridData gd_lblUncheckTheBox = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
        gd_lblUncheckTheBox.widthHint = 565;
        lblUncheckTheBox.setLayoutData(gd_lblUncheckTheBox);
        lblUncheckTheBox.setText("The following pages were automatically classified as development-related.  Please confirm whether each page was classified correctly.  " +
                                  "Uncheck the box if the page is not development-related.  You can double-click a row to open the page in your browser.");
        
        table = new Table(container, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION);
        GridData gd_table = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
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
                        log.error("Exception opening browser on URL " + url, e);
                    }
                }
            }
            
        });
                
        TableViewer viewer = new TableViewer(table);
        ColumnViewerToolTipSupport.enableFor(viewer);
        
        CellLabelProvider labelProvider = new CellLabelProvider() {

            @Override
            public String getToolTipText(Object element) {
                if (element != null) {
                    return element.toString();
                }
                return null;
            }

            @Override
            public void update(ViewerCell arg0) {
            }
        };

        TableViewerColumn titleViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        titleViewerColumn.setLabelProvider(labelProvider);
        
        TableColumn titleColumn = titleViewerColumn.getColumn();
        titleColumn.setResizable(true);
        titleColumn.setText("URL");
        titleColumn.setWidth(291);
        
        TableViewerColumn urlViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        urlViewerColumn.setLabelProvider(labelProvider);

        TableColumn urlColumn = urlViewerColumn.getColumn();
        urlColumn.setResizable(true);
        urlColumn.setText("Page Title");
        urlColumn.setWidth(298);
        
    }
    
    @Override 
    protected void onPageOpened() {
        if (! hasBeenOpened) {
            hasBeenOpened = true;
            
            ClassifierData classifierData = getHistoryMinerData().classifierData;
            
            if (classifierData != null) {
                List<LocationAndVisits> autoCodeRelatedLocations = new ArrayList<LocationAndVisits>();
                autoCodeRelatedLocations.addAll(classifierData.codeRelatedLocations);
                locationsToClassify = new ArrayList<Location>();
                
                Random random = new Random();
                while (locationsToClassify.size() < LOCATIONS_TO_CLASSIFY_COUNT && autoCodeRelatedLocations.size() > 0) {
                    int index = random.nextInt(autoCodeRelatedLocations.size());
                    locationsToClassify.add(autoCodeRelatedLocations.remove(index).location);
                }
    
                table.setItemCount(locationsToClassify.size());
                
                int index = 0;
                for (Location location: locationsToClassify) {
                    TableItem item = table.getItem(index++);
                    item.setText(0, location.url != null ? location.url : "");
                    item.setText(1, location.title != null ? location.title : "");
                    item.setChecked(true);
                }
            }
            
        }
    }
    
    protected void onPageClosing() {
        List<LocationAndClassification> locationsClassified = new ArrayList<LocationAndClassification>();
        int correctlyClassifiedCount = 0;
        for (int i = 0; i < table.getItemCount(); i++) {
            TableItem item = table.getItem(i);
            Location location = locationsToClassify.get(i);
            if (item.getChecked()) {
                correctlyClassifiedCount++;
                locationsClassified.add(new LocationAndClassification(
                        location.id, LocationType.CODE_RELATED, location.url, location.title));
            } else {
                locationsClassified.add(new LocationAndClassification(
                        location.id, LocationType.NON_CODE_RELATED, null, null));
            }
        }
        getHistoryMinerData().locationsManuallyClassified = locationsClassified;
        if (table.getItemCount() > 0) {
            getHistoryMinerData().classifierAccuracy = (double)correctlyClassifiedCount/table.getItemCount();
        }
    }
    
}
