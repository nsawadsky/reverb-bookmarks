# Uninstalling the Eclipse plugin #

  1. In Eclipse, click on Help > Install New Software.
  1. In the lower-right, click on the "What is already installed?" link.
  1. Locate Reverb in the list and click Uninstall.

# Uninstalling the Browser Extensions #

  * In Chrome, click the wrench, then go to Tools > Extensions.  Click on the Remove button next to Reverb.

  * In Firefox, click on the Firefox menu in the top left and select Add-ons.  Click Extensions and then click on the Remove button next to Reverb.

# Uninstalling the Indexing Service #
  1. Go the folder where you originally unzipped the indexing service (if you have forgotten where you placed the files, you can download the zip file again from [here](http://www.cs.ubc.ca/labs/spl/projects/reverb/installreverb.html)).
  1. Run uninstall.bat (Windows) or uninstall.sh (Mac).
  1. The service has now been stopped and unregistered.  You can delete the service files if you wish.

# Deleting Stored Data #
To delete all stored settings and data, including the Reverb index, just remove the data folder. On Windows, this folder is located at `C:\Users\<username>\AppData\Local\cs.ubc.ca\Reverb\data`. On Mac, it is found at `~/.reverb/data`.

To delete the index only, delete the index folder. On Windows, this folder is located at `C:\Users\<username>\AppData\Local\cs.ubc.ca\Reverb\data\db`. On Mac, it is found at `~/.reverb/data/db`.

Note, if the indexing service is still running, deleting the index will fail because files are still in use.  Follow the steps above for "Uninstalling the Indexing Service" to stop and unregister the service.  Then you should be able to delete the folder.  If you wish to continue using Reverb after deleting the index, be sure to re-register the indexing service by running install.bat/install.sh in the folder where you unzipped its files.