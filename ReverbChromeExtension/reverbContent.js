var reverb = {
    startTimer: function(win) {
      var doc = win.document; 
      
      // Ensure we filter out images.
      if (doc.nodeName != "#document") { 
        return; 
      }

      if (win.location.protocol == "about:") {
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
      
      setTimeout(function() { reverb.onPageLoadTimerCallback(win, href); }, 5000);

    },
    
    onPageLoadTimerCallback: function(win, href) {
      var doc = win.document;
      // This catches cases where the tab has been closed, the back button was hit, or a new page was opened in the tab.
      // If the browser window has been closed, the timer never fires.
      if (win.closed || win.location.href != href || doc == null) {
        return;
      } 
      chrome.extension.sendRequest({action: "checkIndexPage", url: win.location.href}, 
          function(response) {
            if (response.indexPage) {
              chrome.extension.sendRequest({action: "updatePageContent", url: win.location.href, page: doc.documentElement.innerHTML});
            }
          });
    },
    
};

reverb.startTimer(window); 

