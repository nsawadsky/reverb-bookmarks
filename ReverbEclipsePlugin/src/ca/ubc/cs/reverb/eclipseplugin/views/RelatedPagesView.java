package ca.ubc.cs.reverb.eclipseplugin.views;


import java.awt.Desktop;
import java.net.URI;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import ca.ubc.cs.reverb.eclipseplugin.EditorMonitor;
import ca.ubc.cs.reverb.eclipseplugin.EditorMonitorListener;
import ca.ubc.cs.reverb.eclipseplugin.IndexerConnection;
import ca.ubc.cs.reverb.eclipseplugin.IndexerConnectionCallback;
import ca.ubc.cs.reverb.eclipseplugin.PluginActivator;
import ca.ubc.cs.reverb.eclipseplugin.PluginConfig;
import ca.ubc.cs.reverb.eclipseplugin.PluginException;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;
import ca.ubc.cs.reverb.eclipseplugin.StudyActivityMonitor;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryResult;
import ca.ubc.cs.reverb.indexer.messages.DeleteLocationRequest;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.LogClickRequest;

public class RelatedPagesView extends ViewPart implements EditorMonitorListener {

    /**
     * The ID of the view as specified by the extension.
     */
    public static final String ID = "ca.ubc.cs.reverb.eclipseplugin.views.RelatedPagesView";
    
    private TreeViewer viewer;
    private ViewContentProvider contentProvider;
    private PluginConfig config;
    private PluginLogger logger;
    private EditorMonitor editorMonitor;
    private IndexerConnection indexerConnection;
    private StudyActivityMonitor studyActivityMonitor;

    class ViewContentProvider implements IStructuredContentProvider, 
            ITreeContentProvider {
        private CodeQueryReply codeQueryReply;
        private final static String NO_RESULTS = "No results available.";
        
        public void setQueryReply(CodeQueryReply reply) {
            this.codeQueryReply = reply;
        }
        
        public CodeQueryReply getQueryReply() {
            return this.codeQueryReply;
        }
        
        public void removeLocation(Location location) {
            for (CodeQueryResult result: codeQueryReply.queryResults) {
                for (Location currLocation: result.locations) {
                    if (currLocation == location) {
                        result.locations.remove(currLocation);
                        return;
                    }
                }
            }
        }
        
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }
        
        public void dispose() {
        }
        
        public Object[] getElements(Object parent) {
            if (parent.equals(getViewSite())) {
                if (codeQueryReply == null || codeQueryReply.queryResults.isEmpty()) {
                    return new Object[] { NO_RESULTS };
                } 
                return codeQueryReply.queryResults.toArray();
            }
            return getChildren(parent);
        }
        
        public Object getParent(Object child) {
            if (child instanceof CodeQueryResult) {
                return null;
            }
            if (codeQueryReply != null && child instanceof Location) {
                for (CodeQueryResult result: codeQueryReply.queryResults) {
                    for (Location location: result.locations) {
                        if (location == child) {
                            return result;
                        }
                    }
                }
                return null;
            }
            return null;
        }
        
        public Object [] getChildren(Object parent) {
            if (parent instanceof CodeQueryResult) {
                return ((CodeQueryResult)parent).locations.toArray();
            }
            return new Object[0];
        }
        
        public boolean hasChildren(Object parent) {
            return (parent instanceof CodeQueryResult && !((CodeQueryResult)parent).locations.isEmpty());
        }
    }
    
    class ViewLabelProvider extends ColumnLabelProvider {
        public String getToolTipText(Object obj) {
            if (obj instanceof Location) {
                return ((Location)obj).url;
            } 
            return super.getToolTipText(obj);
        }

        public String getText(Object obj) {
            if (obj instanceof CodeQueryResult) {
                CodeQueryResult result = (CodeQueryResult)obj;
                return result.displayText;
            } else if (obj instanceof Location) {
                Location loc = (Location)obj;
                String result = loc.title;
                result = result.replace("\n", "");
                if (config.getPluginSettings().isDebugMode) {
                    result += String.format(" (%.1f,%.1f,%.1f)",  
                            loc.relevance, loc.frecencyBoost, loc.overallScore); 
                }
                return result;
            } 
            return obj.toString();
        }
        
        public Image getImage(Object obj) {
            if (obj instanceof CodeQueryResult) {
                return PluginActivator.getDefault().getSearchImage();
            } else if (obj instanceof Location) {
                return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE);
            } else {
                return null;
            }
        }
        
    }
    
    /**
     * The constructor.
     */
    public RelatedPagesView() {
    }

    @Override
    /**
     * Note that the active workbench page is not necessarily available when this is called.
     */
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
    }
    
    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    @Override
    public void createPartControl(Composite parent) {
        try {
            // Pass the page in as a parameter, since it may not yet be available through
            // PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().
            PluginActivator.getDefault().finishInit(getSite().getPage());
        } catch (PluginException e) {
            throw new RuntimeException("Error completing activator initialization: " + e, e);
        }

        this.config = PluginActivator.getDefault().getConfig();
        this.logger = PluginActivator.getDefault().getLogger();
        this.editorMonitor = PluginActivator.getDefault().getEditorMonitor();
        this.indexerConnection = PluginActivator.getDefault().getIndexerConnection();
        this.studyActivityMonitor = PluginActivator.getDefault().getStudyActivityMonitor();
        
        contentProvider = new ViewContentProvider();
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        viewer.setContentProvider(contentProvider);
        ColumnViewerToolTipSupport.enableFor(viewer);
        viewer.setLabelProvider(new ViewLabelProvider());
        viewer.setInput(getViewSite());
        
        final Action openBrowserAction = createOpenBrowserAction();
        final Action deleteLocationAction = createDeleteLocationAction();
        final Action updateViewAction = createUpdateViewAction();
        final Action uploadLogsAction = createUploadLogsAction();
        final Action rateRecommendationsAction = createRateRecommendationsAction();
        
        viewer.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection structured = (IStructuredSelection)viewer.getSelection();
                if (structured.getFirstElement() instanceof Location) {
                    openBrowserAction.setEnabled(true);
                    deleteLocationAction.setEnabled(true);
                } else {
                    openBrowserAction.setEnabled(false);
                    deleteLocationAction.setEnabled(false);
                }
            }
            
        });
        
        viewer.addDoubleClickListener(new IDoubleClickListener() {

            @Override
            public void doubleClick(DoubleClickEvent event) {
                openBrowserAction.run();
            } 
            
        });
        
        MenuManager menuManager = new MenuManager("#PopupMenu");
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener( new IMenuListener() {

            @Override
            public void menuAboutToShow(IMenuManager manager) {
                manager.add(openBrowserAction);
                manager.add(updateViewAction);
                manager.add(deleteLocationAction);
                manager.add(new Separator());
                if (config.getPluginSettings().isDebugMode) {
                    manager.add(uploadLogsAction);
                    manager.add(rateRecommendationsAction);
                }
                manager.add(new Separator());
            }
            
        });
        viewer.getControl().setMenu(menuManager.createContextMenu(viewer.getControl()));
        getSite().registerContextMenu(menuManager, viewer);

        IActionBars bars = getViewSite().getActionBars();

        IToolBarManager toolbarManager = bars.getToolBarManager();
        toolbarManager.add(updateViewAction);

        editorMonitor.addListener(this);
        editorMonitor.requestRefresh(false);
   }

    public void updateView() {
        editorMonitor.requestRefresh(true);
    }
    
    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus() {
        viewer.getControl().setFocus();
    }
    
    @Override
    public void dispose() {
        if (editorMonitor != null) {
            editorMonitor.removeListener(this);
        }
        super.dispose();
    }
    
    @Override 
    public void onCodeQueryReply(CodeQueryReply reply) {
        contentProvider.setQueryReply(reply);
        viewer.refresh();
        viewer.expandAll();
    }

    @Override
    public void onInteractionEvent(long timeMsecs) {
    }

    private Action createUploadLogsAction() {
        Action uploadLogsAction = new Action() {
            public void run() {
                studyActivityMonitor.promptForUploadLogs(false);
            }
        };
                
        uploadLogsAction.setText("Upload Logs");
        uploadLogsAction.setToolTipText("Upload Logs");
        uploadLogsAction.setEnabled(true);
        
        return uploadLogsAction;
    }
    
    private Action createRateRecommendationsAction() {
        Action rateRecommendationsAction = new Action() {
            public void run() {
                studyActivityMonitor.displayRateRecommendationsDialog();
            }
        };
        rateRecommendationsAction.setText("Rate Recommendations");
        rateRecommendationsAction.setToolTipText("Rate Recommendations");
        rateRecommendationsAction.setEnabled(true);

        return rateRecommendationsAction;
    }
    
    private Action createUpdateViewAction() {
        Action updateViewAction = new Action() {
            public void run() {
                editorMonitor.requestRefresh(true);
            }
        };
        updateViewAction.setText("Update Links");
        updateViewAction.setToolTipText("Update Links");
        updateViewAction.setImageDescriptor(PluginActivator.getImageDescriptor("icons/refresh.gif"));

        return updateViewAction;
    }
    
    private Action createOpenBrowserAction() {
        Action openBrowserAction = new Action() {
            public void run() {
                IStructuredSelection structured = (IStructuredSelection)viewer.getSelection();
                if (structured.getFirstElement() instanceof Location) {
                    final Location location = (Location)structured.getFirstElement();
                    BusyIndicator.showWhile(PlatformUI.getWorkbench().getDisplay(), new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Desktop.getDesktop().browse(new URI(location.url));
                            } catch (Exception e) {
                                logger.logError(
                                        "Exception opening browser on '" + location.url + "'", e);
                            }
                        }
                    });
                    long resultGenTimestamp = 0;
                    if (contentProvider.getQueryReply() != null) {
                        resultGenTimestamp = contentProvider.getQueryReply().resultGenTimestamp;
                    }
                    indexerConnection.sendRequestAsync(
                            new LogClickRequest(location, resultGenTimestamp), null, null);
                    studyActivityMonitor.addRecommendationClicked(location, resultGenTimestamp);
                }
            }
        };
        openBrowserAction.setText("Open Page");
        openBrowserAction.setToolTipText("Open Page");
        openBrowserAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
                ISharedImages.IMG_OBJ_FILE));
        openBrowserAction.setEnabled(false);
        
        return openBrowserAction;
    }
    
    private Action createDeleteLocationAction() {
        Action deleteLocationAction = new Action() {
            public void run() {
                IStructuredSelection structured = (IStructuredSelection)viewer.getSelection();
                if (structured.getFirstElement() instanceof Location) {
                    final Location location = (Location)structured.getFirstElement();
                    indexerConnection.sendRequestAsync(
                            new DeleteLocationRequest(location.url), new IndexerConnectionCallback() {

                                @Override
                                public void onIndexerMessage(IndexerMessage message, Object clientInfo) {
                                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

                                        @Override
                                        public void run() {
                                            contentProvider.removeLocation(location);
                                            viewer.refresh();
                                        }
                                    });
                                }

                                @Override
                                public void onIndexerError(String message,
                                        Throwable t) {
                                    MessageDialog.openError(getSite().getShell(), 
                                            "Error Deleting Page", 
                                            "Failed to delete location, the indexer service may not be running.");
                                }
                                
                            }, null);
                }
            }
        };
        deleteLocationAction.setText("Delete Page");
        deleteLocationAction.setToolTipText("Delete Page");
        deleteLocationAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
                ISharedImages.IMG_ETOOL_DELETE));
        deleteLocationAction.setEnabled(false);
        
        return deleteLocationAction;
    }
    
}