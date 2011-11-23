package ca.ubc.cs.reverb.eclipseplugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.IViewportListener;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;
import ca.ubc.cs.reverb.indexer.messages.IndexerBatchQuery;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;

public class EditorMonitor implements IPartListener, IViewportListener {
    private final static int REFRESH_DELAY_MSECS = 3000;
    private final static long INVALID_TIME = -1;
    
    private static EditorMonitor instance = new EditorMonitor();
    private boolean isStarted = false;
    private long lastRefreshTime = INVALID_TIME;
    private IndexerConnection indexerConnection;
    private IWorkbenchPage workbenchPage;
    
    /**
     * Access must be synchronized on the listeners reference.
     */
    private List<EditorMonitorListener> listeners = new ArrayList<EditorMonitorListener>();
    
    private EditorMonitor() {
       
    }

    public static EditorMonitor getDefault() {
        return instance;
    }
    
    public void start(IWorkbenchPage page) throws PluginException {
        if (!isStarted) {
            workbenchPage = page;
            try {
                indexerConnection = new IndexerConnection();
                indexerConnection.start();
            } catch (IOException e) {
                throw new PluginException("Failed to create indexer connection: " + e, e);
            }
    
            workbenchPage.addPartListener(this);
            IWorkbenchPart part = workbenchPage.getActivePart();
            if (part instanceof IEditorPart) {
                addViewportListener((IEditorPart)part);
                startRefreshTimer();
            }
            isStarted = true;
        }
    }
    
    public void addListener(EditorMonitorListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(EditorMonitorListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    @Override
    public void partActivated(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            addViewportListener((IEditorPart)part);
            startRefreshTimer();
        }
    }

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {
    }

    @Override
    public void partClosed(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            removeViewportListener((IEditorPart)part);
        }
    }

    @Override
    public void partDeactivated(IWorkbenchPart part) {
    }

    @Override
    public void partOpened(IWorkbenchPart part) {
    } 
    
    @Override
    public void viewportChanged(int verticalOffset) {
        startRefreshTimer();
    }

    public void startQuery(IEditorPart editorPart) {
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
                Runnable updateViewTask = new Runnable() {

                    @Override
                    public void run() {
                        IDocument doc = textViewer.getDocument();
                        try {
                            int topPosition = doc.getLineOffset(topLine);
                            int bottomPosition = doc.getLineOffset(bottomLine) + doc.getLineLength(bottomLine) - 1;
                            buildAndExecuteQuery((ICompilationUnit)javaElement, 
                                    topPosition, bottomPosition);
                        } catch (Exception e) {
                            getLogger().logError("Error creating/executing query", e);
                        }
                    }
                    
                };
                Thread updateViewThread = new Thread(updateViewTask);
                updateViewThread.setPriority(Thread.MIN_PRIORITY);
                updateViewThread.start();
            }
        } catch (PluginException e) {
            getLogger().logError(e.getMessage(), e);
        }
    }
    
    private void addViewportListener(IEditorPart editorPart) {
        ITextOperationTarget target = (ITextOperationTarget)editorPart.getAdapter(ITextOperationTarget.class);
        if (target == null) {
            return;
        } 
        if (!(target instanceof ITextViewer)) {
            return;
        }
        ITextViewer textViewer = (ITextViewer)target;
        textViewer.addViewportListener(this);
    }
    
    private void removeViewportListener(IEditorPart editorPart) {
        ITextOperationTarget target = (ITextOperationTarget)editorPart.getAdapter(ITextOperationTarget.class);
        if (target == null) {
            return;
        } 
        if (!(target instanceof ITextViewer)) {
            return;
        }
        ITextViewer textViewer = (ITextViewer)target;
        textViewer.removeViewportListener(this);
    }
    
    private void startRefreshTimer() {
        class TimerCallback implements Runnable {
            long startTime = INVALID_TIME;
            
            TimerCallback(long startTime) {
                this.startTime = startTime;
            }
            
            @Override
            public void run() {
                boolean restart = false;
                if (lastRefreshTime != this.startTime) {
                    int newDelay = (int)(lastRefreshTime + REFRESH_DELAY_MSECS - System.currentTimeMillis());
                    if (newDelay >= 500) {
                        // Refresh requested since this timer was started, restart the timer.
                        restart = true;
                        this.startTime = lastRefreshTime;
                        PlatformUI.getWorkbench().getDisplay().timerExec(newDelay, this);
                    }
                }
                if (!restart) {
                    lastRefreshTime = INVALID_TIME;
                    startQuery(workbenchPage.getActiveEditor());
                }
            }
        }
        
        long currentTime = System.currentTimeMillis();
        if (lastRefreshTime == INVALID_TIME || ((currentTime - lastRefreshTime) > (5 * REFRESH_DELAY_MSECS))) {
            lastRefreshTime = System.currentTimeMillis();
            TimerCallback callback = new TimerCallback(lastRefreshTime);
            PlatformUI.getWorkbench().getDisplay().timerExec(REFRESH_DELAY_MSECS, callback);
        } else {
            lastRefreshTime = System.currentTimeMillis();
        }
    }

    private void buildAndExecuteQuery(ICompilationUnit compilationUnit, 
            int topPosition, int bottomPosition) throws InterruptedException, IOException {
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
        
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

            @Override
            public void run() {
                notifyListeners(result);
            }
        });
    }
    
    private void notifyListeners(BatchQueryResult result) {
        List<EditorMonitorListener> listenersCopy = null;
        synchronized (listeners) {
            listenersCopy = new ArrayList<EditorMonitorListener>(listeners);
        }
        for (EditorMonitorListener listener: listenersCopy) {
            try {
                listener.onBatchQueryResult(result);
            } catch (Throwable t) {
                getLogger().logError("Listener threw exception", t);
            }
        }
    }
    
    private void logQueries(List<IndexerQuery> queries) {
        PluginLogger log = getLogger();
        for (IndexerQuery query: queries) {
            log.logInfo("Query display = " + query.queryClientInfo + ", query detail = " + query.queryString);
        }
    }
    
    private PluginLogger getLogger() {
        return PluginActivator.getDefault().getLogger();
    }

}
