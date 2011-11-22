package ca.ubc.cs.reverb.eclipseplugin;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
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
	
	private Image searchImage;
	
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
		plugin = this;
		logger = new PluginLogger(getLog());
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