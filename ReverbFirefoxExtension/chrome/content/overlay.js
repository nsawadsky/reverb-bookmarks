window.addEventListener("load", function(e) { ca_ubc_cs_reverb.onLoad(e); }, false);
window.addEventListener("unload", function(e) { ca_ubc_cs_reverb.onUnload(e); }, false);

var ca_ubc_cs_reverb = {
    onLoad: function() {
      this.consoleService = Components.classes["@mozilla.org/consoleservice;1"].getService(Components.interfaces.nsIConsoleService);
      this.privateBrowsingService = Components.classes["@mozilla.org/privatebrowsing;1"].getService(Components.interfaces.nsIPrivateBrowsingService);

      this.prefsService = Components.classes["@mozilla.org/preferences-service;1"].getService(Components.interfaces.nsIPrefService)  
          .getBranch("extensions.cs.ubc.ca.reverb.");  
      
      Components.utils.import("resource://gre/modules/ctypes.jsm");
      Components.utils.import("resource://gre/modules/AddonManager.jsm");
      
      // Note that the following is an async call.  After this call completes, this.extensionLib may still not be initialized.
      AddonManager.getAddonByID("reverb@cs.ubc.ca", function(addon) { ca_ubc_cs_reverb.finishInit(addon); });
    },
    
   finishInit: function(addon) {
      var uri = addon.getResourceURI("components/windows/ReverbFirefoxExtensionDll.dll");
      if (! (uri instanceof Components.interfaces.nsIFileURL)) {
        Components.utils.reportError("Extension DLL not found");
        return;
      }
      this.extensionLib = ctypes.open(uri.file.path);
      if (this.extensionLib == null) {
        Components.utils.reportError("Failed to load extension DLL");
        return;
      } 
      this.startBackgroundThread = this.extensionLib.declare("RFD_startBackgroundThread", ctypes.default_abi, ctypes.int32_t);
      this.stopBackgroundThread = this.extensionLib.declare("RFD_stopBackgroundThread", ctypes.default_abi, ctypes.int32_t);
      this.sendPage = this.extensionLib.declare("RFD_sendPage", ctypes.default_abi, ctypes.int32_t, ctypes.char.ptr, ctypes.char.ptr);
      this.getErrorMessage = this.extensionLib.declare("RFD_getErrorMessage", ctypes.default_abi, ctypes.void_t, ctypes.char.array(), ctypes.int32_t);
      this.getBackgroundThreadStatus = this.extensionLib.declare("RFD_getBackgroundThreadStatus", ctypes.default_abi, ctypes.void_t, ctypes.char.array(), ctypes.int32_t);

      if (!this.startBackgroundThread()) {
        Components.utils.reportError("Failed to start background thread: " + this.getErrorMessage());
      }

      var appcontent = document.getElementById("appcontent");
      appcontent.addEventListener("DOMContentLoaded", function(e) { ca_ubc_cs_reverb.onPageLoad(e); }, true);
    },

    onUnload: function() {
    	if (this.extensionLib != null) {
    	  this.extensionLib.close();
    	}
    },

    getIgnoredAddresses: function() {
      var ignoredAddresses = this.prefsService.getCharPref("ignoredAddresses");
      if (ignoredAddresses != this.oldIgnoredAddresses) {
        this.oldIgnoredAddresses = ignoredAddresses;
        this.ignoredAddressesArray = ignoredAddresses.toLowerCase().split(',');
        
        for (var i = 0; i < this.ignoredAddressesArray.length; i++) {
          this.ignoredAddressesArray[i] = this.ignoredAddressesArray[i].replace(/^\s+|\s+$/g, "");
        }
      }
      return this.ignoredAddressesArray;
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
      
      // Filter out parent frameset pages (we may still want the child frames, but the frameset parent
      // does not usually have useful content).
      var framesetElements = doc.getElementsByTagName("FRAMESET");
      if (framesetElements != null && framesetElements.length > 0) {
        return;
      }
      
      // Filter out all iframes, as well as frames that reside in a different domain from top window.
      // Note that if the frame/iframe is in a different domain from the top window, Chrome returns 
      // null for win.top and win.frameElement.
      if (win != win.top) {
        if (win.frameElement == null) {
          return;
        }
        if (win.frameElement.tagName == "IFRAME") {
          return;
        }
      }
      
      // Make sure we capture the *current* value of the window's address. 
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
      var tempIgnoredAddresses = this.getIgnoredAddresses();
      if (tempIgnoredAddresses != null && tempIgnoredAddresses.indexOf(win.top.location.host) != -1) {
        return;
      }
      if (!this.sendPage(win.location.href, doc.documentElement.innerHTML)) {
        Components.utils.reportError("Failed to send page: " + this.getErrorMessage());
        Components.utils.reportError("Background thread status: " + this.getBackgroundThreadStatus());
      }
    },
};

