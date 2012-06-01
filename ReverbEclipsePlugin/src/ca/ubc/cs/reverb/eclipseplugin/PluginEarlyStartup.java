package ca.ubc.cs.reverb.eclipseplugin;

import org.eclipse.ui.IStartup;

/**
 * Dummy implementation of the IStartup interface, to try to ensure that our PluginActivator
 * is started on startup of Eclipse.
 */
public class PluginEarlyStartup implements IStartup {

    @Override
    public void earlyStartup() {
    }

}
