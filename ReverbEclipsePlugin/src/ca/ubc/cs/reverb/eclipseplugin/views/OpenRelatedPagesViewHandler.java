package ca.ubc.cs.reverb.eclipseplugin.views;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import ca.ubc.cs.reverb.eclipseplugin.PluginActivator;
import ca.ubc.cs.reverb.eclipseplugin.PluginLogger;

public class OpenRelatedPagesViewHandler implements IHandler {
    @Override
    public void addHandlerListener(IHandlerListener handlerListener) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (page != null) {
            try {
                IViewPart view = page.findView(RelatedPagesView.ID);
                if (view != null && view instanceof RelatedPagesView) {
                    page.activate(view);
                    ((RelatedPagesView)view).updateView();
                } else {
                    page.showView(RelatedPagesView.ID);
                }
            } catch (PartInitException e) {
                getLogger().logError("Error showing related pages view", e);
            }
        }
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isHandled() {
        return true;
    }

    @Override
    public void removeHandlerListener(IHandlerListener handlerListener) {
    }

    @Override
    public void dispose() {
    }
    
    private PluginLogger getLogger() {
        return PluginActivator.getDefault().getLogger();
    }

}
