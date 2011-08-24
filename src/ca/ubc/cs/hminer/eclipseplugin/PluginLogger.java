package ca.ubc.cs.hminer.eclipseplugin;

import java.io.PrintWriter;
import java.io.StringWriter;

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
    
    public void log(int severity, String msg, Throwable t) {
        StringBuilder logMsg = new StringBuilder(msg);
        if (t != null) {
            logMsg.append(": ");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            logMsg.append(sw.toString());
        }
        eclipseLog.log(new Status(severity, PluginActivator.PLUGIN_ID, logMsg.toString(), t));
    }
}
