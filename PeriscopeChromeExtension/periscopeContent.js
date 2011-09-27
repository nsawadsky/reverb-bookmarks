var periscope = {
    addPlugin: function(win) {
      var doc = win.document;
      
      // Ensure we filter out images.
      if (doc.nodeName != "#document") { return; }
      // Filter out popup windows.
      if (win != win.top) { return; }
      // Filter out frames (for now).
      if (win.frameElement != null) { return; }

      var href = win.location.href;
      
      var element = doc.createElement("object");
      element.type = "application/x-periscope";
      element.id = "periscopePlugin";
      element.width = 0;
      element.height = 0;
      doc.body.appendChild(element);
      
      setTimeout(function() { periscope.onPageLoadTimerCallback(doc, win, href); }, 5000);

    },
    
    onPageLoadTimerCallback: function(doc, win, href) {
      if (win.location.href != href) {
        // This catches cases where the tab has been closed, the back button was hit, or a new page was opened in the tab.
        // If the window has been closed, the timer never fires.
      } else {
        var plugin = doc.getElementById("periscopePlugin");
        if (plugin != null) {
          //TODO: Centralize this call.
          plugin.startBackgroundThread();
          if (!plugin.sendPage(doc.location.href, doc.documentElement.innerHTML)) {
            console.log("Failed to send message: " + plugin.getErrorMessage());
            console.log("Background thread status: " + plugin.getBackgroundThreadStatus());
          }
        }
      }
    },
};

periscope.addPlugin(window); 

