package ca.ubc.cs.reverb.eclipseplugin.views;


import java.awt.Desktop;
import java.io.IOException;
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
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.swt.SWT;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;

import ca.ubc.cs.reverb.eclipseplugin.EditorMonitor;
import ca.ubc.cs.reverb.eclipseplugin.IndexerConnection;
import ca.ubc.cs.reverb.eclipseplugin.PluginActivator;
import ca.ubc.cs.reverb.eclipseplugin.PluginException;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;
import ca.ubc.cs.reverb.eclipseplugin.QueryBuilderASTVisitor;
import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;
import ca.ubc.cs.reverb.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.QueryResult;


/**
 * This sample class demonstrates how to plug-in a new
 * workbench view. The view shows data obtained from the
 * model. The sample creates a dummy model on the fly,
 * but a real implementation would connect to the model
 * available either in this or another plug-in (e.g. the workspace).
 * The view is connected to the model using a content provider.
 * <p>
 * The view uses a label provider to define how model
 * objects should be presented in the view. Each
 * view can present the same model objects using
 * different labels and icons, if needed. Alternatively,
 * a single label provider can be shared between views
 * in order to ensure that objects of the same type are
 * presented in the same way everywhere.
 * <p>
 */

public class RelatedPagesView extends ViewPart {

    /**
     * The ID of the view as specified by the extension.
     */
    public static final String ID = "ca.ubc.cs.reverb.eclipseplugin.views.RelatedPagesView";
    
    private TreeViewer viewer;
    private ViewContentProvider contentProvider;
    private IndexerConnection indexerConnection;

    class ViewContentProvider implements IStructuredContentProvider, 
            ITreeContentProvider {
        private BatchQueryResult batchQueryResult;
        private String message = "Select 'Update View' to get results.";
        
        public void setQueryResult(BatchQueryResult result) {
            this.message = null;
            this.batchQueryResult = result;
        }
        
        public void setMessage(String message) {
            this.batchQueryResult = null;
            this.message = message;
        }
        
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }
        
        public void dispose() {
        }
        
        public Object[] getElements(Object parent) {
            if (parent.equals(getViewSite())) {
                if (message != null) {
                    return new Object[] { message };
                } 
                List<QueryResult> filteredResults = new ArrayList<QueryResult>();
                for (QueryResult result: batchQueryResult.queryResults) {
                    if (result.locations != null && !result.locations.isEmpty()) {
                        filteredResults.add(result);
                    }
                }
                return filteredResults.toArray();
            }
            return getChildren(parent);
        }
        
        public Object getParent(Object child) {
            if (child instanceof QueryResult) {
                return null;
            }
            if (batchQueryResult != null && child instanceof Location) {
                for (QueryResult result: batchQueryResult.queryResults) {
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
            String imageKey = ISharedImages.IMG_OBJ_FILE;
            if (obj instanceof QueryResult) {
                return PluginActivator.getDefault().getSearchImage();
            }
            return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE);
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

        try {
            indexerConnection = new IndexerConnection();
            indexerConnection.start();
        } catch (Exception e) {
            throw new PartInitException("Error initializing Reverb view: " + e, e);
        }
    }
    
    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    public void createPartControl(Composite parent){
        // Start the monitor here -- if we start it in init(), it sometimes cannot get the active page.
        EditorMonitor.getDefault().start();

        contentProvider = new ViewContentProvider();
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        viewer.setContentProvider(contentProvider);
        ColumnViewerToolTipSupport.enableFor(viewer);
        viewer.setLabelProvider(new ViewLabelProvider());
        viewer.setInput(getViewSite());
        
        final Action openBrowserAction = new Action() {
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
        
        viewer.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection structured = (IStructuredSelection)viewer.getSelection();
                if (structured.getFirstElement() instanceof Location) {
                    openBrowserAction.setEnabled(true);
                } else {
                    openBrowserAction.setEnabled(false);
                }
            }
            
        });
        
        viewer.addDoubleClickListener(new IDoubleClickListener() {

            @Override
            public void doubleClick(DoubleClickEvent event) {
                openBrowserAction.run();
            } 
            
        });
        
        final Action updateViewAction = new Action() {
            public void run() {
                updateLinks(getSite().getPage().getActiveEditor());
            }
        };
        updateViewAction.setText("Update View");
        updateViewAction.setToolTipText("Update View");
        updateViewAction.setImageDescriptor(PluginActivator.getImageDescriptor("icons/refresh.gif"));

        MenuManager menuManager = new MenuManager("#PopupMenu");
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener( new IMenuListener() {

            @Override
            public void menuAboutToShow(IMenuManager manager) {
                manager.add(openBrowserAction);
                manager.add(updateViewAction);
                manager.add(new Separator());
                // Other plug-ins can contribute there actions here
                manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
            }
            
        });
        viewer.getControl().setMenu(menuManager.createContextMenu(viewer.getControl()));
        getSite().registerContextMenu(menuManager, viewer);

        IActionBars bars = getViewSite().getActionBars();
        /*
        IMenuManager barMenuManager = bars.getMenuManager();
        barMenuManager.add(openBrowserAction);
        barMenuManager.add(updateViewAction);
        */

        IToolBarManager toolbarManager = bars.getToolBarManager();
        toolbarManager.add(updateViewAction);
   }
    
    @Override 
    public void dispose() {
        if (indexerConnection != null) {
            try {
                indexerConnection.stop();
            } catch (IOException e) { }
            indexerConnection = null;
        }
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus() {
        viewer.getControl().setFocus();
    }
    
    private IStatus buildAndExecuteQuery(ICompilationUnit compilationUnit, 
            int topPosition, int bottomPosition, IProgressMonitor monitor) throws InterruptedException, IOException {
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(compilationUnit);
        parser.setResolveBindings(true);
        parser.setStatementsRecovery(true);
        CompilationUnit compileUnit = (CompilationUnit)parser.createAST(null);
        QueryBuilderASTVisitor visitor = new QueryBuilderASTVisitor(compileUnit.getAST(), topPosition, bottomPosition);
        compileUnit.accept(visitor);
        
        List<IndexerQuery> queries = visitor.getQueries();
        //logQueries(queries);
        
        final BatchQueryResult result = indexerConnection.runQuery(new IndexerBatchQuery(visitor.getQueries()), 20000);
        
        getSite().getShell().getDisplay().asyncExec(new Runnable() {

            @Override
            public void run() {
                contentProvider.setQueryResult(result);
                viewer.refresh();
                viewer.expandAll();
            }
        });
        
        return new Status(IStatus.OK, PluginActivator.PLUGIN_ID, "Updated Reverb view successfully");
    }
    
    private void logQueries(List<IndexerQuery> queries) {
        PluginLogger log = getLogger();
        for (IndexerQuery query: queries) {
            log.logInfo("Query display = " + query.queryClientInfo + ", query detail = " + query.queryString);
        }
    }
    
    private void updateLinks(IEditorPart editorPart) {
        try {
            if (editorPart != null) {
                ITextOperationTarget target = (ITextOperationTarget)editorPart.getAdapter(ITextOperationTarget.class);
                if (target == null) {
                    throw new PluginException("Failed to get ITextOperationTarget adapter");
                } 
                if (!(target instanceof ITextViewer)) {
                    throw new PluginException("ITextOperationTarget adapter is not instance of ITextViewer");
                }
                final ITextViewer textViewer = (ITextViewer)target;
                if (!(editorPart instanceof AbstractTextEditor)) {
                    throw new PluginException("Editor part is not instance of AbstractTextEditor");
                } 
                IEditorInput editorInput = editorPart.getEditorInput();
                if (editorInput == null) {
                    throw new PluginException("Editor input is null");
                } 
                final IJavaElement javaElement = JavaUI.getEditorInputJavaElement(editorInput);
                if (javaElement == null) {
                    throw new PluginException("Failed to get Java element from editor input");
                } 
                if (!(javaElement instanceof ICompilationUnit)) {
                    throw new PluginException("Editor input Java element is not instance of ICompilationUnit");
                } 
                final int topLine = textViewer.getTopIndex();
                final int bottomLine = textViewer.getBottomIndex();

                Job updateViewJob = new Job("Update View") {

                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        IDocument doc = textViewer.getDocument();
                        try {
                            int topPosition = doc.getLineOffset(topLine);
                            int bottomPosition = doc.getLineOffset(bottomLine) + doc.getLineLength(bottomLine) - 1;
                            return buildAndExecuteQuery((ICompilationUnit)javaElement, 
                                    topPosition, bottomPosition, monitor);
                        } catch (Exception e) {
                            String msg = "Error creating/executing query";
                            getLogger().logError(msg, e);
                            return new Status(IStatus.ERROR, PluginActivator.PLUGIN_ID, msg, e);
                        }
                    }
                    
                };
                updateViewJob.schedule();
            }
        } catch (PluginException e) {
            getLogger().logError(e.getMessage(), e);
        }
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