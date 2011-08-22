window.addEventListener("load", function(e) { historyMiner.onLoad(e); }, false);
window.addEventListener("unload", function(e) { historyMiner.onUnload(e); }, false);

var historyMiner = {
    onLoad: function() {
      historyMiner.consoleService = Components.classes["@mozilla.org/consoleservice;1"].getService(Components.interfaces.nsIConsoleService);
      
      Components.utils.import("resource://gre/modules/ctypes.jsm");
      Components.utils.import("resource://gre/modules/AddonManager.jsm");
      
      // Note that the following is an async call.  After this call completes, historyMiner.extensionLib may still not be initialized.
      AddonManager.getAddonByID("historyminer@cs.ubc.ca", function(addon)
      {
          var uri = addon.getResourceURI("components/windows/HistoryMinerExtensionDll.dll");
          if (uri instanceof Components.interfaces.nsIFileURL)
          {
        	  historyMiner.extensionLib = ctypes.open(uri.file.path);
              if (historyMiner.extensionLib != null) {
                  historyMiner.startBackgroundThread = historyMiner.extensionLib.declare("HMED_startBackgroundThread", ctypes.default_abi, ctypes.bool);
                  historyMiner.stopBackgroundThread = historyMiner.extensionLib.declare("HMED_stopBackgroundThread", ctypes.default_abi, ctypes.bool);
                  historyMiner.sendPage = historyMiner.extensionLib.declare("HMED_sendPage", ctypes.default_abi, ctypes.bool, ctypes.jschar.ptr, ctypes.jschar.ptr);
                  historyMiner.getErrorMessage = historyMiner.extensionLib.declare("HMED_getErrorMessage", ctypes.default_abi, ctypes.void_t, ctypes.jschar.array(), ctypes.int32_t);
                  historyMiner.getBackgroundThreadStatus = historyMiner.extensionLib.declare("HMED_getBackgroundThreadStatus", ctypes.default_abi, ctypes.void_t, ctypes.jschar.array(), ctypes.int32_t);

                  historyMiner.startBackgroundThread();
              }
          }
          
          //historyMiner.extensionLib = ctypes.open("C:\\Users\\Nick\\git\\HistoryMiner\\HistoryMinerExtensionDll\\Debug\\HistoryMinerExtensionDll.dll");
      });
      
      var appcontent = document.getElementById("appcontent");
      if (appcontent != null) {
        appcontent.addEventListener("DOMContentLoaded", historyMiner.onPageLoad, true);
      }
    },
    
    onUnload: function() {
    	if (historyMiner.extensionLib != null) {
    		historyMiner.extensionLib.close();
    	}
    },

    onPageLoad: function(event) {
      var doc = event.originalTarget; 
      var win = doc.defaultView;
      // Ensure we filter out images.
      if (doc.nodeName != "#document") { return; }
      // Filter out popup windows.
      if (win != win.top) { return; }
      // Filter out frames (for now).
      if (win.frameElement != null) { return; }
      historyMiner.consoleService.logStringMessage("Page loaded: " + doc.documentURI);

      if (historyMiner.sendPage != null) {
        if (!historyMiner.sendPage(doc.location.href, doc.documentElement.innerHTML)) {
          var BUF_LEN = 1024;
          
          var buffer = ctypes.jschar.array(BUF_LEN)();
          historyMiner.getErrorMessage(buffer, BUF_LEN);
          historyMiner.consoleService.logStringMessage("Failed to send message: " + buffer.readString());
          historyMiner.getBackgroundThreadStatus(buffer, BUF_LEN);
          historyMiner.consoleService.logStringMessage("Background thread status: " + buffer.readString());
        }
      }
      
    },

    onMenuItemCommand: function() {
      window.open("chrome://historyminer/content/historyminer.xul", "", "chrome");
    }
};

