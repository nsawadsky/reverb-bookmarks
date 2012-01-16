package ca.ubc.cs.reverb.eclipseplugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import ca.ubc.cs.reverb.indexer.messages.CodeQueryReply;
import ca.ubc.cs.reverb.indexer.messages.CodeQueryRequest;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;

public class EditorMonitor implements IPartListener, MouseListener, KeyListener {
    private final static int REFRESH_DELAY_MSECS = 1000;
    private final static long INVALID_TIME = -1;
    
    private static EditorMonitor instance = new EditorMonitor();
    private boolean isStarted = false;
    private long lastRequestRefreshTime = INVALID_TIME;
    private IndexerConnection indexerConnection;
    private IWorkbenchPage workbenchPage;
    
    private ITextViewer lastTextViewer = null;
    private int lastTopLine = 0;
    private int lastBottomLine = 0;
    
    /**
     * Access must be synchronized on the listeners reference.
     */
    private List<EditorMonitorListener> listeners = new ArrayList<EditorMonitorListener>();
    
    /**
     * Set of editor parts we are already listening to.  Access must be synchronized on the listenedParts reference.
     */
    private Set<IEditorPart> listenedParts = new HashSet<IEditorPart>();
    
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
                if (listen((IEditorPart)part)) {
                    handleNavigationEvent();
                }
            }
            isStarted = true;
        }
    }
    
    public IndexerConnection getIndexerConnection() {
        return indexerConnection;
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
            IEditorPart editorPart = (IEditorPart)part;
            if (listen(editorPart)) {
                handleNavigationEvent();
            }
        }
    }

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {
    }

    @Override
    public void partClosed(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            stopListening((IEditorPart)part);
        }
    }

    @Override
    public void partDeactivated(IWorkbenchPart part) {
    }

    @Override
    public void partOpened(IWorkbenchPart part) {
    } 
    
    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
        handleNavigationEvent();
    }

    @Override
    public void mouseDoubleClick(MouseEvent e) {
        handleNavigationEvent();
    }

    @Override
    public void mouseDown(MouseEvent e) {
    }

    @Override
    public void mouseUp(MouseEvent e) {
        handleNavigationEvent();
    }

    public void requestRefresh(boolean immediate) {
        // Ensure that an update will be sent.
        lastTextViewer = null;
        if (immediate) {
            doRefresh();
        } else {
            startRefreshTimer();
        }
    }
    
    private void doRefresh() {
        try {
            IEditorPart editorPart = workbenchPage.getActiveEditor();
            if (editorPart != null) {
                ITextViewer textViewer = getTextViewer(editorPart);
                if (textViewer == null) {
                    throw new PluginException("Failed to get text viewer");
                }
                final ICompilationUnit compileUnit = getCompilationUnit(editorPart);
                if (compileUnit == null) {
                    throw new PluginException("Failed to get compilation unit");
                }
                int topLine = textViewer.getTopIndex();
                int bottomLine = textViewer.getBottomIndex();
                
                if (!textViewer.equals(lastTextViewer) || topLine != lastTopLine || bottomLine != lastBottomLine) {
                    lastTextViewer = textViewer;
                    lastTopLine = topLine;
                    lastBottomLine = bottomLine;
                    
                    IDocument doc = textViewer.getDocument();
                    final int topPosition = doc.getLineOffset(topLine);
                    final int bottomPosition = doc.getLineOffset(bottomLine) + doc.getLineLength(bottomLine) - 1;
                    
                    Runnable updateViewTask = new Runnable() {

                        @Override
                        public void run() {
                            try {
                                buildAndExecuteQuery(compileUnit, topPosition, bottomPosition);
                            } catch (Exception e) {
                                getLogger().logError("Error creating/executing query", e);
                            }
                        }
                        
                    };
                    Thread updateViewThread = new Thread(updateViewTask);
                    updateViewThread.setPriority(Thread.MIN_PRIORITY);
                    updateViewThread.start();
                }
            }
        } catch (Exception e) {
            getLogger().logError("Error refreshing result list", e);
        }
    }
    
    private void buildAndExecuteQuery(ICompilationUnit compilationUnit, 
            int topPosition, int bottomPosition) throws InterruptedException, IOException {
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(compilationUnit);
        parser.setResolveBindings(true);
        parser.setStatementsRecovery(true);
        CompilationUnit compileUnit = (CompilationUnit)parser.createAST(null);
        CodeElementExtractor visitor = new CodeElementExtractor(compileUnit.getAST(), topPosition, bottomPosition);
        compileUnit.accept(visitor);
        
        final CodeQueryReply reply = indexerConnection.sendCodeQueryRequest(new CodeQueryRequest(visitor.getCodeElements()), 20000);
        
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

            @Override
            public void run() {
                notifyListeners(reply);
            }
        });
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
                if (lastRequestRefreshTime != this.startTime) {
                    int newDelay = (int)(lastRequestRefreshTime + REFRESH_DELAY_MSECS - System.currentTimeMillis());
                    if (newDelay >= 100) {
                        // Refresh requested since this timer was started, restart the timer.
                        restart = true;
                        this.startTime = lastRequestRefreshTime;
                        PlatformUI.getWorkbench().getDisplay().timerExec(newDelay, this);
                    }
                }
                if (!restart) {
                    lastRequestRefreshTime = INVALID_TIME;
                    doRefresh();
                }
            }
        }
        
        long currentTime = System.currentTimeMillis();
        if (lastRequestRefreshTime == INVALID_TIME || ((currentTime - lastRequestRefreshTime) > (5 * REFRESH_DELAY_MSECS))) {
            lastRequestRefreshTime = System.currentTimeMillis();
            TimerCallback callback = new TimerCallback(lastRequestRefreshTime);
            PlatformUI.getWorkbench().getDisplay().timerExec(REFRESH_DELAY_MSECS, callback);
        } else {
            lastRequestRefreshTime = System.currentTimeMillis();
        }
    }

    private void handleNavigationEvent() {
        startRefreshTimer();
    }
    
    private boolean listen(IEditorPart editorPart) {
        if (isListenedPart(editorPart)) {
            return true;
        }
        if (getTextViewer(editorPart) != null && getCompilationUnit(editorPart) != null) {
            Control control = (Control)editorPart.getAdapter(Control.class);
            control.addKeyListener(this);
            control.addMouseListener(this);
            addToListenedParts(editorPart);
            return true;
        }
        return false;
    }
    
    private void stopListening(IEditorPart editorPart) {
        if (isListenedPart(editorPart)) {
            Control control = (Control)editorPart.getAdapter(Control.class);
            control.removeKeyListener(this);
            control.removeMouseListener(this);

            removeFromListenedParts(editorPart);
        }
    }
    
    private ITextViewer getTextViewer(IEditorPart editorPart) {
        ITextOperationTarget target = (ITextOperationTarget)editorPart.getAdapter(ITextOperationTarget.class);
        if (!(target instanceof ITextViewer)) {
            return null;
        }
        return (ITextViewer)target;
    }
    
    private ICompilationUnit getCompilationUnit(IEditorPart editorPart) {
        IEditorInput editorInput = editorPart.getEditorInput();
        if (editorInput == null) {
            return null;
        } 
        IJavaElement javaElement = JavaUI.getEditorInputJavaElement(editorInput);
        if (!(javaElement instanceof ICompilationUnit)) {
            return null;
        }
        return (ICompilationUnit)javaElement;
    }
    
    private void notifyListeners(CodeQueryReply reply) {
        List<EditorMonitorListener> listenersCopy = null;
        synchronized (listeners) {
            listenersCopy = new ArrayList<EditorMonitorListener>(listeners);
        }
        for (EditorMonitorListener listener: listenersCopy) {
            try {
                listener.onCodeQueryReply(reply);
            } catch (Throwable t) {
                getLogger().logError("Listener threw exception", t);
            }
        }
    }
    
    private void addToListenedParts(IEditorPart editorPart) {
        synchronized(listenedParts) {
            listenedParts.add(editorPart);
        }
    }
    
    private void removeFromListenedParts(IEditorPart editorPart) {
        synchronized(listenedParts) {
            listenedParts.remove(editorPart);
        }
    }
    
    private boolean isListenedPart(IEditorPart editorPart) {
        synchronized(listenedParts) {
            return listenedParts.contains(editorPart);
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
