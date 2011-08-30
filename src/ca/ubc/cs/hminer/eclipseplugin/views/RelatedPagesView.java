package ca.ubc.cs.hminer.eclipseplugin.views;

import java.util.ArrayList;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.*;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.*;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.SWT;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.JavaUI;

import ca.ubc.cs.hminer.eclipseplugin.PluginActivator;
import ca.ubc.cs.hminer.eclipseplugin.PluginLogger;


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

    private TreeViewer viewer;
    private DrillDownAdapter drillDownAdapter;
    private Action action1;
    private Action action2;
    private Action doubleClickAction;

    /*
     * The content provider class is responsible for
     * providing objects to the view. It can wrap
     * existing objects in adapters or simply return
     * objects as-is. These objects may be sensitive
     * to the current input of the view, or ignore
     * it and always show the same content 
     * (like Task List, for example).
     */

    class TreeObject implements IAdaptable {
        private String name;
        private TreeParent parent;

        public TreeObject(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        public void setParent(TreeParent parent) {
            this.parent = parent;
        }
        public TreeParent getParent() {
            return parent;
        }
        public String toString() {
            return getName();
        }
        public Object getAdapter(Class key) {
            return null;
        }
    }

    class TreeParent extends TreeObject {
        private ArrayList<TreeObject> children;
        public TreeParent(String name) {
            super(name);
            children = new ArrayList<TreeObject>();
        }
        public void addChild(TreeObject child) {
            children.add(child);
            child.setParent(this);
        }
        public void removeChild(TreeObject child) {
            children.remove(child);
            child.setParent(null);
        }
        public TreeObject [] getChildren() {
            return (TreeObject [])children.toArray(new TreeObject[children.size()]);
        }
        public boolean hasChildren() {
            return children.size()>0;
        }
    }

    class ViewContentProvider implements IStructuredContentProvider, 
            ITreeContentProvider {
        private TreeParent invisibleRoot;

        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }
        public void dispose() {
        }
        public Object[] getElements(Object parent) {
            if (parent.equals(getViewSite())) {
                if (invisibleRoot==null) initialize();
                return getChildren(invisibleRoot);
            }
            return getChildren(parent);
        }
        public Object getParent(Object child) {
            if (child instanceof TreeObject) {
                return ((TreeObject)child).getParent();
            }
            return null;
        }
        public Object [] getChildren(Object parent) {
            if (parent instanceof TreeParent) {
                return ((TreeParent)parent).getChildren();
            }
            return new Object[0];
        }
        public boolean hasChildren(Object parent) {
            if (parent instanceof TreeParent)
                return ((TreeParent)parent).hasChildren();
            return false;
        }
        /*
         * We will set up a dummy model to initialize tree hierarchy.
         * In a real code, you will connect to a real model and
         * expose its hierarchy.
         */
        private void initialize() {
            /*
            TreeObject to1 = new TreeObject("Leaf 1");
            TreeObject to2 = new TreeObject("Leaf 2");
            TreeObject to3 = new TreeObject("Leaf 3");
            TreeParent p1 = new TreeParent("Parent 1");
            p1.addChild(to1);
            p1.addChild(to2);
            p1.addChild(to3);

            TreeObject to4 = new TreeObject("Leaf 4");
            TreeParent p2 = new TreeParent("Parent 2");
            p2.addChild(to4);

            TreeParent root = new TreeParent("Root");
            root.addChild(p1);
            root.addChild(p2);
            */

            invisibleRoot = new TreeParent("");
            //invisibleRoot.addChild(root);
        }
    }
    class ViewLabelProvider extends LabelProvider {

        public String getText(Object obj) {
            return obj.toString();
        }
        public Image getImage(Object obj) {
            String imageKey = ISharedImages.IMG_OBJ_ELEMENT;
            if (obj instanceof TreeParent)
                imageKey = ISharedImages.IMG_OBJ_FOLDER;
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
    }

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
        
        NavigationListener navListener = new NavigationListener();
        site.getWorkbenchWindow().getSelectionService().addSelectionListener(navListener);
        site.getPage().addPartListener(navListener);
    }
    
    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    public void createPartControl(Composite parent) {
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        drillDownAdapter = new DrillDownAdapter(viewer);
        viewer.setContentProvider(new ViewContentProvider());
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

    private void makeActions() {
        action1 = new Action() {
            public void run() {
                IEditorPart editorPart = getSite().getPage().getActiveEditor();
                if (editorPart instanceof ITextEditor) {
                    
                }
                if (editorPart != null) {
                    IEditorInput editorInput = editorPart.getEditorInput();
                    if (editorInput == null) {
                        getLogger().logError("Editor input is null", null);
                    } else {
                        IJavaElement javaElement = JavaUI.getEditorInputJavaElement(editorInput);
                        if (javaElement == null) {
                            getLogger().logError("Failed to get Java element from editor input");
                        } else if (!(javaElement instanceof ICompilationUnit)) {
                            getLogger().logError("Editor input Java element is not instance of ICompilationUnit");
                        } else {
                            ASTParser parser = ASTParser.newParser(AST.JLS3);
                            parser.setSource((ICompilationUnit)javaElement);
                            parser.setResolveBindings(true);
                            CompilationUnit compileUnit = (CompilationUnit)parser.createAST(new IProgressMonitor() {

                                @Override
                                public void beginTask(String arg0, int arg1) {
                                }

                                @Override
                                public void done() {
                                }

                                @Override
                                public void internalWorked(double arg0) {
                                }

                                @Override
                                public boolean isCanceled() {
                                    return false;
                                }

                                @Override
                                public void setCanceled(boolean arg0) {
                                }

                                @Override
                                public void setTaskName(String arg0) {
                                }

                                @Override
                                public void subTask(String arg0) {
                                }

                                @Override
                                public void worked(int arg0) {
                                }
                                
                            });
                                    
                        }
                    }
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