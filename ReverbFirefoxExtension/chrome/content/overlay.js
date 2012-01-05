window.addEventListener("load", function(e) { ca_ubc_cs_reverb.onLoad(e); }, false);
window.addEventListener("unload", function(e) { ca_ubc_cs_reverb.onUnload(e); }, false);

var ca_ubc_cs_reverb = {
    MAX_INDEX_HISTORY_LENGTH: 100,

    indexedAddresses: new Array(),

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

    getLastIndexTime: function(address) {
      address = this.removeFragment(address);
      for (var i = 0; i < this.indexedAddresses.length; i++) {
        if (this.indexedAddresses[i].address == address) {
          return this.indexedAddresses[i].indexTime;
        }
      }  
      return null;
    },
    
    addToIndexHistory: function(address) {
      address = this.removeFragment(address);
      for (var i = 0; i < this.indexedAddresses.length; i++) {
        if (this.indexedAddresses[i].address == address) {
          this.indexedAddresses.splice(i, 1);
          break;
        }
      }
      this.indexedAddresses.push({address: address, indexTime: new Date()});
      if (this.indexedAddresses.length > this.MAX_INDEX_HISTORY_LENGTH) {
        this.indexedAddresses.shift();
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
      
      // Filter out all iframes, as well as frames that reside in a different domain from top window.
      // For all frames and iframes, Chrome returns null for win.top.
      // If the frame/iframe is in a different domain from the top window, Chrome returns 
      // null for win.frameElement and logs an error message in the console.
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
    
    onPageLoadTimerCallback: function(win, oldHref) {
      var doc = win.document;
      // The following lines catch cases where the tab has been closed, the back button was hit, or a new page was opened in the tab.
      // If the browser window has been closed, the timer never fires.
      if (win.closed || doc == null) {
        return;
      }
      
      if (this.removeFragment(win.location.href) != this.removeFragment(oldHref)) {
        return;
      } 
      
      var tempIgnoredAddresses = this.getIgnoredAddresses();
      if (tempIgnoredAddresses != null && tempIgnoredAddresses.indexOf(win.top.location.host) != -1) {
        return;
      }
      
      var lastIndexTime = this.getLastIndexTime(win.location.href);
      if (lastIndexTime != null && 
          (new Date().getTime() - lastIndexTime.getTime() < 24 * 60 * 60 * 1000)) {
        return;
      }
      
      if (this.sendPage(win.location.href, doc.documentElement.innerHTML)) {
        this.addToIndexHistory(win.location.href);
      } else {
        Components.utils.reportError("Failed to send page: " + this.getErrorMessage());
        Components.utils.reportError("Background thread status: " + this.getBackgroundThreadStatus());
      }
    },

    removeFragment: function(url) {
      var hashIndex = url.lastIndexOf("#");
      hashIndex = (hashIndex == -1 ? url.length : hashIndex);
      return url.substring(0, hashIndex);
    }
    
};

