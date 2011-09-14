window.addEventListener("load", function(e) { historyMiner.onLoad(e); }, false);
window.addEventListener("unload", function(e) { historyMiner.onUnload(e); }, false);

var historyMiner = {
    onLoad: function() {
      this.consoleService = Components.classes["@mozilla.org/consoleservice;1"].getService(Components.interfaces.nsIConsoleService);
      
      Components.utils.import("resource://gre/modules/ctypes.jsm");
      Components.utils.import("resource://gre/modules/AddonManager.jsm");
      
      // Note that the following is an async call.  After this call completes, this.extensionLib may still not be initialized.
      AddonManager.getAddonByID("historyminer@cs.ubc.ca", function(addon)
      {
          var uri = addon.getResourceURI("components/windows/HistoryMinerExtensionDll.dll");
          if (uri instanceof Components.interfaces.nsIFileURL)
          {
            historyMiner.finishInit(uri.file.path);
          }
          
          //this.extensionLib = ctypes.open("C:\\Users\\Nick\\git\\HistoryMiner\\HistoryMinerExtensionDll\\Debug\\HistoryMinerExtensionDll.dll");
      });
      
      var appcontent = document.getElementById("appcontent");
      if (appcontent != null) {
        appcontent.addEventListener("DOMContentLoaded", this.onPageLoad, true);
      }
    },
    
    finishInit: function(extensionLibPath) {
      this.extensionLib = ctypes.open(extensionLibPath);
      if (this.extensionLib != null) {
        this.startBackgroundThread = this.extensionLib.declare("HMED_startBackgroundThread", ctypes.default_abi, ctypes.bool);
        this.stopBackgroundThread = this.extensionLib.declare("HMED_stopBackgroundThread", ctypes.default_abi, ctypes.bool);
        this.sendPage = this.extensionLib.declare("HMED_sendPage", ctypes.default_abi, ctypes.bool, ctypes.jschar.ptr, ctypes.jschar.ptr);
        this.getErrorMessage = this.extensionLib.declare("HMED_getErrorMessage", ctypes.default_abi, ctypes.void_t, ctypes.jschar.array(), ctypes.int32_t);
        this.getBackgroundThreadStatus = this.extensionLib.declare("HMED_getBackgroundThreadStatus", ctypes.default_abi, ctypes.void_t, ctypes.jschar.array(), ctypes.int32_t);

        this.startBackgroundThread();
      }
    },

    onUnload: function() {
    	if (this.extensionLib != null) {
    	  this.extensionLib.close();
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

      var href = win.location.href;
      
      // TODO: Make list of domains to filter configurable.
      if (href.indexOf("http://www.google.") != 0) {
        setTimeout(function() {historyMiner.onPageLoadTimerCallback(doc, win, href);}, 5000);
      }
    },
    
    onPageLoadTimerCallback: function(doc, win, href) {
      if (win.location.href != href) {
        // This catches cases where the tab has been closed, the back button was hit, or a new page was opened in the tab.
        // If the Firefox window has been closed, the timer never fires.
      } else {
        if (this.sendPage != null) {
          if (!this.sendPage(doc.location.href, doc.documentElement.innerHTML)) {
            var BUF_LEN = 1024;

            var buffer = ctypes.jschar.array(BUF_LEN)();
            this.getErrorMessage(buffer, BUF_LEN);
            this.consoleService.logStringMessage("Failed to send message: " + buffer.readString());
            this.getBackgroundThreadStatus(buffer, BUF_LEN);
            this.consoleService.logStringMessage("Background thread status: " + buffer.readString());
          }
        }
      }
    },

    onMenuItemCommand: function() {
      window.open("chrome://historyminer/content/historyminer.xul", "", "chrome");
    }
};

