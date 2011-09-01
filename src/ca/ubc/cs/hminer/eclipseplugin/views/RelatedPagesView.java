package ca.ubc.cs.hminer.eclipseplugin.views;

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
import org.eclipse.swt.widgets.Menu;
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
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;

import ca.ubc.cs.hminer.eclipseplugin.PluginActivator;
import ca.ubc.cs.hminer.eclipseplugin.PluginLogger;
import ca.ubc.cs.hminer.eclipseplugin.PluginException;
import ca.ubc.cs.hminer.indexer.messages.BatchQueryResult;
import ca.ubc.cs.hminer.indexer.messages.Location;
import ca.ubc.cs.hminer.indexer.messages.QueryResult;


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
    public static final String ID = "ca.ubc.cs.hminer.eclipseplugin.views.ResultsView";
    
    private final static String UPDATE_VIEW_ERROR_MSG = "Error updating view.";

    private TreeViewer viewer;
    private DrillDownAdapter drillDownAdapter;
    private Action action1;
    private Action action2;
    private Action doubleClickAction;
    private ViewContentProvider contentProvider;

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
                return batchQueryResult.queryResults.toArray();
            }
            return getChildren(parent);
        }
        
        public Object getParent(Object child) {
            if (child instanceof QueryResult) {
                return null;
            }
            if (batchQueryResult != null && child instanceof Location) {
                for (QueryResult result: batchQueryResult.queryResults) {
                    // TODO: check containment using reference equality
                    if (result.locations.contains(child)) {
                        return result;
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
    
    class ViewLabelProvider extends LabelProvider {

        public String getText(Object obj) {
            if (obj instanceof QueryResult) {
                return ((QueryResult)obj).query;
            } else if (obj instanceof Location) {
                return ((Location)obj).title;
            } 
            return obj.toString();
        }
        
        public Image getImage(Object obj) {
            String imageKey = ISharedImages.IMG_OBJ_ELEMENT;
            if (obj instanceof QueryResult) {
                imageKey = ISharedImages.IMG_OBJ_FOLDER;
            }
            return PlatformUI.getWorkbench().getSharedImages().getImage(imageKey);
        }
    }
    
    class NameSorter extends ViewerSorter {
    }

    class NavigationListener implements IPartListener, ISelectionListener {

        @Override
        public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        }

        @Override
        public void partActivated(IWorkbenchPart part) {
        }

        @Override
        public void partBroughtToTop(IWorkbenchPart part) {
        }

        @Override
        public void partClosed(IWorkbenchPart part) {
        }

        @Override
        public void partDeactivated(IWorkbenchPart part) {
        }

        @Override
        public void partOpened(IWorkbenchPart part) {
        } 
        
    }
    
    /**
     * The constructor.
     */
    public RelatedPagesView() {
        contentProvider = new ViewContentProvider();
    }

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
        
        /*
        NavigationListener navListener = new NavigationListener();
        site.getWorkbenchWindow().getSelectionService().addSelectionListener(navListener);
        site.getPage().addPartListener(navListener);
        */
    }
    
    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    public void createPartControl(Composite parent) {
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        drillDownAdapter = new DrillDownAdapter(viewer);
        viewer.setContentProvider(contentProvider);
        viewer.setLabelProvider(new ViewLabelProvider());
        viewer.setSorter(new NameSorter());
        viewer.setInput(getViewSite());
        makeActions();
        hookContextMenu();
        hookDoubleClickAction();
        contributeToActionBars();
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus() {
        viewer.getControl().setFocus();
    }
    
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#PopupMenu");
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager) {
                RelatedPagesView.this.fillContextMenu(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuMgr, viewer);
    }

    private void contributeToActionBars() {
        IActionBars bars = getViewSite().getActionBars();
        fillLocalPullDown(bars.getMenuManager());
        fillLocalToolBar(bars.getToolBarManager());
    }

    private void fillLocalPullDown(IMenuManager manager) {
        manager.add(action1);
        manager.add(new Separator());
        manager.add(action2);
    }

    private void fillContextMenu(IMenuManager manager) {
        manager.add(action1);
        manager.add(action2);
        manager.add(new Separator());
        drillDownAdapter.addNavigationActions(manager);
        // Other plug-ins can contribute there actions here
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    }

    private void fillLocalToolBar(IToolBarManager manager) {
        manager.add(action1);
        manager.add(action2);
        manager.add(new Separator());
        drillDownAdapter.addNavigationActions(manager);
    }

    private IStatus buildAndExecuteQuery(ICompilationUnit compilationUnit, 
            int topLineIndex, int bottomLineIndex, IProgressMonitor monitor) {
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(compilationUnit);
        CompilationUnit compileUnit = (CompilationUnit)parser.createAST(null);
        List<String> queryStrings = new ArrayList<String>();
        compileUnit.
        
        return new Status(IStatus.OK, PluginActivator.PLUGIN_ID, "Updated Related Pages view successfully");
    }
    
    private void makeActions() {
        action1 = new Action() {
            public void run() {
                try {
                    IEditorPart editorPart = getSite().getPage().getActiveEditor();
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
                        contentProvider.setMessage("Updating view ...");
                        viewer.refresh();
                        Job updateViewJob = new Job("Update Related Pages") {

                            @Override
                            protected IStatus run(IProgressMonitor monitor) {
                                return buildAndExecuteQuery((ICompilationUnit)javaElement, 
                                        textViewer.getTopIndex(), textViewer.getBottomIndex(),
                                        monitor);
                            }
                            
                        };
                        updateViewJob.schedule();
                    }
                } catch (PluginException e) {
                    getLogger().logError(e.getMessage(), e);
                    contentProvider.setMessage(UPDATE_VIEW_ERROR_MSG);
                    viewer.refresh();
                }
            }
        };
        action1.setText("Update");
        action1.setToolTipText("Update View");
        action1.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
                getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));

        action2 = new Action() {
            public void run() {
                showMessage("Action 2 executed");
            }
        };
        action2.setText("Action 2");
        action2.setToolTipText("Action 2 tooltip");
        action2.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
                getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
        doubleClickAction = new Action() {
            public void run() {
                ISelection selection = viewer.getSelection();
                Object obj = ((IStructuredSelection)selection).getFirstElement();
                showMessage("Double-click detected on "+obj.toString());
            }
        };
    }

    private void hookDoubleClickAction() {
        viewer.addDoubleClickListener(new IDoubleClickListener() {
            public void doubleClick(DoubleClickEvent event) {
                doubleClickAction.run();
            }
        });
    }
    private void showMessage(String message) {
        MessageDialog.openInformation(
                viewer.getControl().getShell(),
                "Related Pages",
                message);
    }

    private PluginLogger getLogger() {
        return PluginActivator.getDefault().getLogger();
    }
}