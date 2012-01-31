package ca.ubc.cs.reverb.eclipseplugin;

import java.io.IOException;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class PluginActivator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "ca.ubc.cs.reverb.eclipseplugin"; //$NON-NLS-1$

	// The shared instance
	private static PluginActivator plugin;
	
	private PluginLogger logger;
	
	private PluginConfig config;
	
	private EditorMonitor editorMonitor;
	
	private IndexerConnection indexerConnection;
	
	private Image searchImage;
	
	private boolean initComplete = false;
	
	/**
	 * The constructor
	 */
	public PluginActivator() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);

        config = new PluginConfig();
		logger = new PluginLogger(getLog());
		
		IWorkbench workbench = PlatformUI.getWorkbench();
		if (workbench == null) {
		    throw new PluginException("Failed to get workbench during startup");
		}
		workbench.getDisplay().asyncExec(new Runnable() {
            public void run() {
                try {
                    finishInit();
                } catch (PluginException e) { }
            }
        });
        
        plugin = this;
	}

	/** 
	 * We defer creation of indexerConnection, because it references Jackson JSON 
	 * classes, whose use of class loading conflicts with OSGi class loading during
	 * Eclipse startup, resulting in timeout errors in the Eclipse log (most 
	 * likely caused by classloader deadlocks).
	 */
	public void finishInit() throws PluginException {
	    if (!initComplete) {
	        try {
    	        indexerConnection = new IndexerConnection(logger);
    
    	        indexerConnection.start();

    	        editorMonitor = new EditorMonitor(logger, indexerConnection);
                
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window == null) {
                    throw new PluginException("Failed to get workbench window during startup");
                }
                IWorkbenchPage activePage = window.getActivePage();
                if (activePage == null) {
                    throw new PluginException("Failed to get active workbench page during startup");
                }
                editorMonitor.start(window.getActivePage());
	        } catch (Exception e) { 
	            String errorMsg = "Error completing plugin initialization"; 
	            logger.logError(errorMsg, e);
	            if (e instanceof PluginException) {
	                throw (PluginException)e;
	            }
	            throw new PluginException(errorMsg + ": " + e, e);
	        } 
	        
	        initComplete = true;
	    }
	}
	
    /*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
        plugin = null;
	    try {
	        indexerConnection.stop();
	    } catch (IOException e) { }
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static PluginActivator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
	
	public PluginLogger getLogger() {
	    return this.logger;
	}
	
	public PluginConfig getConfig() {
	    return this.config;
	}
	
	public EditorMonitor getEditorMonitor() {
	    return this.editorMonitor;
	}
	
	public IndexerConnection getIndexerConnection() {
	    return this.indexerConnection;
	}
	
    public Image getSearchImage() {
        if (searchImage == null) {
            ImageDescriptor descriptor = getImageDescriptor("icons/search.gif");
            if (descriptor != null) {
                searchImage = descriptor.createImage();
            }
        }
        return searchImage;
    }

}
