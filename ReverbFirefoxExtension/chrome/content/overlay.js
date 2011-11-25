window.addEventListener("load", function(e) { ca_ubc_cs_reverb.onLoad(e); }, false);
window.addEventListener("unload", function(e) { ca_ubc_cs_reverb.onUnload(e); }, false);

var ca_ubc_cs_reverb = {
    onLoad: function() {
      this.consoleService = Components.classes["@mozilla.org/consoleservice;1"].getService(Components.interfaces.nsIConsoleService);
      this.privateBrowsingService = Components.classes["@mozilla.org/privatebrowsing;1"].getService(Components.interfaces.nsIPrivateBrowsingService);

      this.prefsService = Components.classes["@mozilla.org/preferences-service;1"].getService(Components.interfaces.nsIPrefService)  
          .getBranch("extensions.cs.ubc.ca.reverb.");  
      this.prefsService.QueryInterface(Components.interfaces.nsIPrefBranch2);  
      
      Components.utils.import("resource://gre/modules/ctypes.jsm");
      Components.utils.import("resource://gre/modules/AddonManager.jsm");
      
      // Note that the following is an async call.  After this call completes, this.extensionLib may still not be initialized.
      AddonManager.getAddonByID("reverb@cs.ubc.ca", function(addon)
      {
          var uri = addon.getResourceURI("components/windows/ReverbFirefoxExtensionDll.dll");
          if (uri instanceof Components.interfaces.nsIFileURL)
          {
            ca_ubc_cs_reverb.finishInit(uri.file.path);
          }
      });
      
      var appcontent = document.getElementById("appcontent");
      if (appcontent != null) {
        appcontent.addEventListener("DOMContentLoaded", function(e) { ca_ubc_cs_reverb.onPageLoad(e); }, true);
      }
    },
    
    getIgnoredAddresses: function() {
      var prefString = this.prefsService.getCharPref("ignoredAddresses");
      if (prefString == null) {
        return null;
      }
      prefString = prefString.toLowerCase();
      var ignoredAddresses = prefString.split(',');
      
      for (var i = 0; i < ignoredAddresses.length; i++) {
        ignoredAddresses[i] = ignoredAddresses[i].replace(" ", "");
      }
      return ignoredAddresses;
    },
    
   finishInit: function(extensionLibPath) {
      this.extensionLib = ctypes.open(extensionLibPath);
      if (this.extensionLib != null) {
        this.startBackgroundThread = this.extensionLib.declare("RFD_startBackgroundThread", ctypes.default_abi, ctypes.int32_t);
        this.stopBackgroundThread = this.extensionLib.declare("RFD_stopBackgroundThread", ctypes.default_abi, ctypes.int32_t);
        this.sendPage = this.extensionLib.declare("RFD_sendPage", ctypes.default_abi, ctypes.int32_t, ctypes.char.ptr, ctypes.char.ptr);
        this.getErrorMessage = this.extensionLib.declare("RFD_getErrorMessage", ctypes.default_abi, ctypes.void_t, ctypes.char.array(), ctypes.int32_t);
        this.getBackgroundThreadStatus = this.extensionLib.declare("RFD_getBackgroundThreadStatus", ctypes.default_abi, ctypes.void_t, ctypes.char.array(), ctypes.int32_t);

        this.startBackgroundThread();
      }
    },

    onUnload: function() {
    	if (this.extensionLib != null) {
    	  this.extensionLib.close();
    	}
    },

    onPageLoad: function(event) {
      if (this.privateBrowsingService.privateBrowsingEnabled) {
        return;
      }
      var doc = event.originalTarget; 
      var win = doc.defaultView;
      
      // Ensure we filter out images.
      if (doc.nodeName != "#document") { 
        return; 
      }

      if (win.location.protocol == "about:") {
        return;
      }
      
      // Filter out frames from different origins.
      var topHost = win.top.location.host;
      if (win.location.host != topHost) {
        return;
      }
      
      // Filter out frames which are hidden.
      if (win.frameElement != null) {
        if (win.frameElement.style.visibility == "hidden" || win.frameElement.style.display == "none" ||
            win.frameElement.getAttribute("aria-hidden") == "true") {
          return;
        }
      }
      
      var href = win.location.href;
      
      setTimeout(function() { ca_ubc_cs_reverb.onPageLoadTimerCallback(win, href); }, 5000);
    },
    
    onPageLoadTimerCallback: function(win, href) {
      var doc = win.document;
      // This catches cases where the tab has been closed, the back button was hit, or a new page was opened in the tab.
      // If the browser window has been closed, the timer never fires.
      if (win.closed || win.location.href != href || doc == null) {
        return;
      } 
      var ignoredAddresses = this.getIgnoredAddresses();
      if (ignoredAddresses != null && ignoredAddresses.indexOf(win.top.location.host) != -1) {
        return;
      }
      if (this.sendPage != null) {
        this.sendPage(win.location.href, doc.documentElement.innerHTML);
      }
    },
    
    onMenuItemCommand: function() {
      window.open("chrome://reverb/content/reverb.xul", "", "chrome");
    }
};

