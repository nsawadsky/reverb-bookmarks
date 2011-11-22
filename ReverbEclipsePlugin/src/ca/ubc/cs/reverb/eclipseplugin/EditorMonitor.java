package ca.ubc.cs.reverb.eclipseplugin;

import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.IViewportListener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

public class EditorMonitor implements IPartListener, IViewportListener {
    private final static int REFRESH_DELAY_MSECS = 5000;
    private final static long INVALID_TIME = -1;
    
    private static EditorMonitor instance = new EditorMonitor();
    private boolean isStarted = false;
    private long lastRefreshTime = INVALID_TIME;
    
    public static EditorMonitor getDefault() {
        return instance;
    }
    
    public void start(IWorkbenchPage page) throws PluginException {
        if (!isStarted) {
            page.addPartListener(this);
            IWorkbenchPart part = page.getActivePart();
            if (part instanceof IEditorPart) {
                addViewportListener((IEditorPart)part);
                startRefreshTimer();
            }
            isStarted = true;
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
                        getLogger().logInfo("Restarting timer");
                    }
                }
                if (!restart) {
                    getLogger().logInfo("Timer timed out");
                    lastRefreshTime = INVALID_TIME;
                }
            }
        }
        
        long currentTime = System.currentTimeMillis();
        if (lastRefreshTime == INVALID_TIME || ((currentTime - lastRefreshTime) > (5 * REFRESH_DELAY_MSECS))) {
            lastRefreshTime = System.currentTimeMillis();
            TimerCallback callback = new TimerCallback(lastRefreshTime);
            PlatformUI.getWorkbench().getDisplay().timerExec(REFRESH_DELAY_MSECS, callback);
            getLogger().logInfo("Starting timer");
        } else {
            lastRefreshTime = System.currentTimeMillis();
            getLogger().logInfo("Resetting timer");
        }
    }

    private EditorMonitor() {
        
    }

    private PluginLogger getLogger() {
        return PluginActivator.getDefault().getLogger();
    }

}
