#/**********************************************************\ 
#
# Auto-Generated Plugin Configuration file
# for Reverb
#
#\**********************************************************/

set(PLUGIN_NAME "Reverb")
set(PLUGIN_PREFIX "REV")
set(COMPANY_NAME "UBCSPL")

# ActiveX constants:
set(FBTYPELIB_NAME ReverbLib)
set(FBTYPELIB_DESC "Reverb 1.0 Type Library")
set(IFBControl_DESC "Reverb Control Interface")
set(FBControl_DESC "Reverb Control Class")
set(IFBComJavascriptObject_DESC "Reverb IComJavascriptObject Interface")
set(FBComJavascriptObject_DESC "Reverb ComJavascriptObject Class")
set(IFBComEventSource_DESC "Reverb IFBComEventSource Interface")
set(AXVERSION_NUM "1")

# NOTE: THESE GUIDS *MUST* BE UNIQUE TO YOUR PLUGIN/ACTIVEX CONTROL!  YES, ALL OF THEM!
set(FBTYPELIB_GUID 7706b0a0-29b1-5bd3-a87b-cdbc39295bd8)
set(IFBControl_GUID 63b9253b-a78e-5c42-8a55-fdfef2ae7145)
set(FBControl_GUID 8af76274-fc78-518c-bfea-f14700a84d01)
set(IFBComJavascriptObject_GUID 0731be16-065d-5d38-87d3-5039fa0fdc73)
set(FBComJavascriptObject_GUID 3f4f7ed8-4ccb-5e57-8243-23407b0b492f)
set(IFBComEventSource_GUID 21a7d3ee-237a-5694-a06b-84ca6fb567c8)

# these are the pieces that are relevant to using it from Javascript
set(ACTIVEX_PROGID "UBCSPL.Reverb")
set(MOZILLA_PLUGINID "cs.ubc.ca/Reverb")

# strings
set(FBSTRING_CompanyName "UBC Software Practices Lab")
set(FBSTRING_FileDescription "Indexes web pages for quick retrieval inside the IDE.")
set(FBSTRING_PLUGIN_VERSION "1.0.0.0")
set(FBSTRING_LegalCopyright "Copyright 2011 UBC Software Practices Lab")
set(FBSTRING_PluginFileName "np${PLUGIN_NAME}.dll")
set(FBSTRING_ProductName "Reverb")
set(FBSTRING_FileExtents "")
set(FBSTRING_PluginName "Reverb")
set(FBSTRING_MIMEType "application/x-reverb")

# Uncomment this next line if you're not planning on your plugin doing
# any drawing:

set (FB_GUI_DISABLED 1)

# Mac plugin settings. If your plugin does not draw, set these all to 0
set(FBMAC_USE_QUICKDRAW 0)
set(FBMAC_USE_CARBON 0)
set(FBMAC_USE_COCOA 0)
set(FBMAC_USE_COREGRAPHICS 0)
set(FBMAC_USE_COREANIMATION 0)
set(FBMAC_USE_INVALIDATINGCOREANIMATION 0)

# If you want to register per-machine on Windows, uncomment this line
#set (FB_ATLREG_MACHINEWIDE 1)
