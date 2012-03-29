package ca.ubc.cs.reverb.eclipseplugin;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

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
	
	private StudyActivityMonitor studyActivityMonitor;
	
	private IndexerConnection indexerConnection;
	
	private Image searchImage;
	
	private boolean initComplete = false;
	
	private Version bundleVersion = null;
	
	/**
	 * The constructor
	 */
	public PluginActivator() {
	}

	/**
     * Early startup is enabled for the plugin.  It's important to note that, with early startup, start() 
     * may be invoked in a separate thread from the UI thread.
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);

        plugin = this;
        
        bundleVersion = context.getBundle().getVersion();

		IWorkbench workbench = PlatformUI.getWorkbench();
		if (workbench == null) {
		    throw new PluginException("Failed to get workbench during startup");
		}
		workbench.getDisplay().asyncExec(new Runnable() {
            public void run() {
                try {
                    finishInit(null);
                } catch (PluginException e) { }
            }
        });
	}

	/** 
	 * We defer creation of config, indexerConnection and studyActivityMonitor, because they reference Jackson JSON 
	 * classes, whose use of class loading conflicts with OSGi class loading during
	 * Eclipse startup, was observed to cause timeout errors in the Eclipse log (most 
	 * likely caused by classloader deadlocks).  *** In recent tests, these conflicts are no longer reproducible.
	 * 
	 * In addition, the workbench window and active page may not yet be available when the start() method is
	 * called.
	 * 
	 * The above issues may be partly due to enabling early startup for the plugin.  With early startup, start() 
	 * may be invoked in a separate thread from the UI thread.
	 * 
	 * @param activePage The active workbench page.  If null, then this method attempts to retrieve
	 *                   the active page from the workbench.
	 */
	public void finishInit(IWorkbenchPage activePage) throws PluginException {
	    if (!initComplete) {
	        try {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window == null) {
                    throw new PluginException("Failed to get workbench window during startup");
                }
                if (activePage == null) {
                    activePage = window.getActivePage();
                    if (activePage == null) {
                        throw new PluginException("Failed to get active workbench page during startup");
                    }
                }

                config = new PluginConfig();
                logger = new PluginLogger(getLog());
                
                indexerConnection = new IndexerConnection(config, logger);
    
    	        editorMonitor = new EditorMonitor(logger, indexerConnection);
                
    	        studyActivityMonitor = new StudyActivityMonitor(window.getShell(), config, logger,
    	                indexerConnection, editorMonitor);
    	        
                indexerConnection.start();

                editorMonitor.start(activePage);
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
	
	public StudyActivityMonitor getStudyActivityMonitor() {
	    return this.studyActivityMonitor;
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
    
    public Version getBundleVersion() {
        return bundleVersion;
    }

    public String getBundleVersionString(boolean includeQualifier) {
        if (bundleVersion == null) {
            return "";
        }
        String result = Integer.toString(bundleVersion.getMajor()) +
                "." + bundleVersion.getMinor() + "." + bundleVersion.getMicro();
        if (includeQualifier) {
            result += "." + bundleVersion.getQualifier();
        }
        return result;
    }
}
