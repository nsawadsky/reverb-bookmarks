#/**********************************************************\ 
#
# Auto-Generated Plugin Configuration file
# for Periscope
#
#\**********************************************************/

set(PLUGIN_NAME "Periscope")
set(PLUGIN_PREFIX "PER")
set(COMPANY_NAME "UBCSPL")

# ActiveX constants:
set(FBTYPELIB_NAME PeriscopeLib)
set(FBTYPELIB_DESC "Periscope 1.0 Type Library")
set(IFBControl_DESC "Periscope Control Interface")
set(FBControl_DESC "Periscope Control Class")
set(IFBComJavascriptObject_DESC "Periscope IComJavascriptObject Interface")
set(FBComJavascriptObject_DESC "Periscope ComJavascriptObject Class")
set(IFBComEventSource_DESC "Periscope IFBComEventSource Interface")
set(AXVERSION_NUM "1")

# NOTE: THESE GUIDS *MUST* BE UNIQUE TO YOUR PLUGIN/ACTIVEX CONTROL!  YES, ALL OF THEM!
set(FBTYPELIB_GUID 04a54dc0-8dd7-5eb2-8c87-ff47bac26c3a)
set(IFBControl_GUID 7ba74197-07ac-5f50-9874-448836716f3a)
set(FBControl_GUID 9b01fb97-03c0-5a86-ae9a-266d887bb0a7)
set(IFBComJavascriptObject_GUID 4f8ead64-f4bf-53aa-b01f-fe0041958ce3)
set(FBComJavascriptObject_GUID 961ea324-3952-5140-8f2d-d636a102a5a2)
set(IFBComEventSource_GUID 7d1c4433-d280-5a57-b652-4f661f7c04a9)

# these are the pieces that are relevant to using it from Javascript
set(ACTIVEX_PROGID "UBCSPL.Periscope")
set(MOZILLA_PLUGINID "cs.ubc.ca/Periscope")

# strings
set(FBSTRING_CompanyName "UBC Software Practices Lab")
set(FBSTRING_FileDescription "Indexes web pages for quick retrieval inside the IDE.")
set(FBSTRING_PLUGIN_VERSION "1.0.0.0")
set(FBSTRING_LegalCopyright "Copyright 2011 UBC Software Practices Lab")
set(FBSTRING_PluginFileName "np${PLUGIN_NAME}.dll")
set(FBSTRING_ProductName "Periscope")
set(FBSTRING_FileExtents "")
set(FBSTRING_PluginName "Periscope")
set(FBSTRING_MIMEType "application/x-periscope")

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
