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
      
      // Filter out all iframes, as well as frames that reside in a different domain from top window.
      // Note that if the frame/iframe is in a different domain from the top window, Chrome returns 
      // null for win.top and win.frameElement.
      if (win != win.top) {
        if (win.frameElement == null) {
          return;
        }
        if (win.frameElement.tagName == "IFRAME") {
          console.log("Filtering iframe");
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
      chrome.extension.sendRequest({action: "checkIndexPage", url: win.location.href, isFrame: (win != win.top)}, 
          function(response) {
            if (response.indexPage) {
              chrome.extension.sendRequest({action: "updatePageContent", url: win.location.href, page: doc.documentElement.innerHTML});
            }
          });
    },
    
};

reverb.startTimer(window); 

