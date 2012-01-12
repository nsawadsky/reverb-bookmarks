package ca.ubc.cs.reverb.eclipseplugin.views;


import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.*;
import org.eclipse.swt.SWT;

import ca.ubc.cs.reverb.eclipseplugin.EditorMonitor;
import ca.ubc.cs.reverb.eclipseplugin.EditorMonitorListener;
import ca.ubc.cs.reverb.eclipseplugin.IndexerConnectionCallback;
import ca.ubc.cs.reverb.eclipseplugin.PluginActivator;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;
import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryResult;
import ca.ubc.cs.reverb.indexer.messages.DeleteLocationRequest;
import ca.ubc.cs.reverb.indexer.messages.IndexerMessage;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.QueryResult;

public class RelatedPagesView extends ViewPart implements EditorMonitorListener {

    /**
     * The ID of the view as specified by the extension.
     */
    public static final String ID = "ca.ubc.cs.reverb.eclipseplugin.views.RelatedPagesView";
    
    private TreeViewer viewer;
    private ViewContentProvider contentProvider;

    class ViewContentProvider implements IStructuredContentProvider, 
            ITreeContentProvider {
        private CodeQueryReply codeQueryReply;
        private final static String NO_RESULTS = "No results available.";
        
        public void setQueryReply(CodeQueryReply reply) {
            this.codeQueryReply = reply;
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
            if (child instanceof QueryResult) {
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
            if (parent instanceof QueryResult) {
                return ((QueryResult)parent).locations.toArray();
            }
            return new Object[0];
        }
        
        public boolean hasChildren(Object parent) {
            return (parent instanceof QueryResult && !((QueryResult)parent).locations.isEmpty());
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
            if (obj instanceof QueryResult) {
                QueryResult result = (QueryResult)obj;
                List<String> keywords = new ArrayList<String>();
                for (IndexerQuery query: result.indexerQueries) {
                    String[] words = query.queryClientInfo.split(" ");
                    if (words != null) {
                        for (String word: words) {
                            if (!keywords.contains(word)) {
                                keywords.add(word);
                            }
                        }
                    }
                }
                StringBuilder display = new StringBuilder();
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) {
                        display.append(" ");
                    }
                    display.append(keywords.get(i));
                }
                return display.toString();
            } else if (obj instanceof Location) {
                Location loc = (Location)obj;
                return String.format("%s (%.1f,%.1f,%.1f)", loc.title, loc.luceneScore, loc.frecencyBoost, loc.overallScore);
            } 
            return obj.toString();
        }
        
        public Image getImage(Object obj) {
            if (obj instanceof QueryResult) {
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
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
    }
    
    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    public void createPartControl(Composite parent){
        contentProvider = new ViewContentProvider();
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        viewer.setContentProvider(contentProvider);
        ColumnViewerToolTipSupport.enableFor(viewer);
        viewer.setLabelProvider(new ViewLabelProvider());
        viewer.setInput(getViewSite());
        
        final Action openBrowserAction = createOpenBrowserAction();
        final Action deleteLocationAction = createDeleteLocationAction();
        final Action updateViewAction = createUpdateViewAction();
        
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
                // Other plug-ins can contribute there actions here
                manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
            }
            
        });
        viewer.getControl().setMenu(menuManager.createContextMenu(viewer.getControl()));
        getSite().registerContextMenu(menuManager, viewer);

        IActionBars bars = getViewSite().getActionBars();

        IToolBarManager toolbarManager = bars.getToolBarManager();
        toolbarManager.add(updateViewAction);

        EditorMonitor.getDefault().start(getSite().getPage());
        EditorMonitor.getDefault().addListener(this);
        EditorMonitor.getDefault().requestRefresh(false);
   }

    public void updateView() {
        EditorMonitor.getDefault().requestRefresh(true);
    }
    
    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus() {
        viewer.getControl().setFocus();
    }
    
    @Override
    public void dispose() {
        super.dispose();
        EditorMonitor.getDefault().removeListener(this);
    }
    
    @Override
    public void onBatchQueryReply(BatchQueryReply result) {
    }
    
    @Override 
    public void onCodeQueryReply(CodeQueryReply reply) {
        contentProvider.setQueryReply(reply);
        viewer.refresh();
        viewer.expandAll();
    }

    private Action createUpdateViewAction() {
        Action updateViewAction = new Action() {
            public void run() {
                EditorMonitor.getDefault().requestRefresh(true);
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
                    Location location = (Location)structured.getFirstElement();
                    try {
                        Desktop.getDesktop().browse(new URI(location.url));
                    } catch (Exception e) {
                        getLogger().logError(
                                "Exception opening browser on '" + location.url + "'", e);
                    }
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
                    EditorMonitor.getDefault().getIndexerConnection().sendRequestAsync(
                            new DeleteLocationRequest(location.url), new IndexerConnectionCallback() {

                                @Override
                                public void onIndexerMessage(
                                        IndexerMessage message,
                                        Object clientInfo) {
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
    
    private void showMessage(String message) {
        MessageDialog.openInformation(
                viewer.getControl().getShell(),
                "Reverb",
                message);
    }

    private PluginLogger getLogger() {
        return PluginActivator.getDefault().getLogger();
    }

}