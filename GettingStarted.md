# Getting the Tool #

To join the user study and give Reverb a try, first complete the consent form [here](http://www.cs.ubc.ca/labs/spl/projects/reverb/consentform.html).

# Getting Started #

## Configure Domains to Exclude from Index ##

Reverb indexes pages in the background as you browse the web.  The index is stored under your user profile folder.

You can configure domains to be excluded from the index.  Pages under these domains will never be indexed.  By default, the domains www.google.com and www.google.ca are excluded.  To configure additional domains to exclude, follow the following steps.

  * In Chrome, click the wrench menu.  Go to Tools > Extensions.  Next to Reverb, click Options.  Specify domains to exclude under Ignored Sites.
  * In Firefox, click the Firefox menu in the upper left and select Add-ons.  Click Extensions and then click the Options button next to Reverb.  Specify domains to exclude under Ignored Sites.

## Using the Eclipse Plugin ##

  * To display the Reverb view in Eclipse, go to Window > Show View > Other > Reverb.

  * The hotkey to open the view (or refresh the results, if it is already open) is Ctrl + Shift + ] (right square bracket).

  * Results will only be available if you have a Java code file open.

  * Results are automatically refreshed whenever you move to a new part of the file.  You can manually force a refresh through the refresh button at the top-right of the view, or through the key combination Ctrl + Shift + ] (right square bracket).

  * The queries for the indexing service are built using the code that is currently visible within the editor window.

  * Double-click on a result to open it in your browser.

  * To delete a result from the index, right-click on it and select Delete Page.  It will not show up again, unless you happen to open it again in your browser.

  * To block a particular type from your results, right-click on a query that begins with that type, and select `Block package.TypeName`.

## More Information ##

  * [Data Collected for User Study](DataCollection.md)
  * [Uninstalling Reverb](UninstallingReverb.md)
  * [Google Group for Reverb](https://groups.google.com/forum/#!forum/reverb-users)