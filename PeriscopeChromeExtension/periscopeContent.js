var periscope = {
    startTimer: function(win) {
      var doc = win.document;
      
      // Ensure we filter out images.
      if (doc.nodeName != "#document") { return; }
      // Filter out popup windows.
      if (win != win.top) { return; }
      // Filter out frames (for now).
      if (win.frameElement != null) { return; }

      var href = win.location.href;
      
      setTimeout(function() { periscope.onPageLoadTimerCallback(doc, win, href); }, 5000);

    },
    
    onPageLoadTimerCallback: function(doc, win, href) {
      if (win.location.href != href) {
        // This catches cases where the tab has been closed, the back button was hit, or a new page was opened in the tab.
        // If the window has been closed, the timer never fires.
      } else {
        chrome.extension.sendRequest({url: doc.location.href, page: doc.documentElement.innerHTML});
      }
    },
};

periscope.startTimer(window); 

