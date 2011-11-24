var reverb = {
    startTimer: function(win) {
      var doc = win.document;
      
      // Ensure we filter out images.
      if (doc.nodeName != "#document") { return; }

      var href = win.location.href;
      
      setTimeout(function() { reverb.onPageLoadTimerCallback(win, href); }, 5000);

    },
    
    onPageLoadTimerCallback: function(win, href) {
      if (win.location.href != href) {
        // This catches cases where the tab has been closed, the back button was hit, or a new page was opened in the tab.
        // If the browser window has been closed, the timer never fires.
      } else {
        this.sendWindowContent(win);
      }
    },
    
    sendWindowContent: function(win) {
      var doc = win.document;
      if (doc == null) {
        return;
      }
      chrome.extension.sendRequest({url: win.location.href, page: doc.documentElement.innerHTML});
    },

};

reverb.startTimer(window); 

