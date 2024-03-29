package ca.ubc.cs.reverb.eclipseplugin;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public class PluginLogger {
    private ILog eclipseLog;
    
    public PluginLogger(ILog eclipseLog) {
        this.eclipseLog = eclipseLog;
    }
    
    public void logError(String msg) {
        logError(msg, null);
    }
    
    public void logError(String msg, Throwable t) {
        log(IStatus.ERROR, msg, t);
    }
    
    public void logWarn(String msg) {
        logWarn(msg, null);
    }
    
    public void logWarn(String msg, Throwable t) {
        log(IStatus.WARNING, msg, t);
    }

    public void logInfo(String msg) {
        logInfo(msg, null);
    }
    
    public void logInfo(String msg, Throwable t) {
        log(IStatus.INFO, msg, t);
    }
    
    public void log(int severity, String msg, Throwable t) {
        log(createStatus(severity, msg, t));
    }
    
    public void log(IStatus status) {
        eclipseLog.log(status);
    }
    
    public IStatus createStatus(int severity, String msg, Throwable t) {
        StringBuilder logMsg = new StringBuilder(msg);
        if (t != null) {
            logMsg.append(": ");
            logMsg.append(t.toString());
        }
        return new Status(severity, PluginActivator.PLUGIN_ID, logMsg.toString(), t);
    }
}
