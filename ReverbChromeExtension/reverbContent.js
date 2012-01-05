var reverbContent = {
    startTimer: function(win) {
      var doc = win.document; 
      
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
      
      setTimeout(function() { reverbContent.onPageLoadTimerCallback(win, href); }, 5000);

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
      
      chrome.extension.sendRequest({action: "checkIndexPage", url: win.location.href}, 
          function(response) {
            if (response.indexPage) {
              chrome.extension.sendRequest({action: "updatePageContent", url: win.location.href, page: doc.documentElement.innerHTML});
            }
          });
    },
    
    removeFragment: function(url) {
      var hashIndex = url.lastIndexOf("#");
      hashIndex = (hashIndex == -1 ? url.length : hashIndex);
      return url.substring(0, hashIndex);
    }
    
};

reverbContent.startTimer(window); 

