/**********************************************************\

  Auto-generated PeriscopeAPI.cpp

\**********************************************************/

#include "JSObject.h"
#include "variant_list.h"
#include "DOM/Document.h"

#include "PeriscopeAPI.h"
#include "PeriscopeIndexerClient.h"

///////////////////////////////////////////////////////////////////////////////
/// @fn PeriscopeAPI::PeriscopeAPI(const PeriscopePtr& plugin, const FB::BrowserHostPtr host)
///
/// @brief  Constructor for your JSAPI object.  You should register your methods, properties, and events
///         that should be accessible to Javascript from here.
///
/// @see FB::JSAPIAuto::registerMethod
/// @see FB::JSAPIAuto::registerProperty
/// @see FB::JSAPIAuto::registerEvent
///////////////////////////////////////////////////////////////////////////////
PeriscopeAPI::PeriscopeAPI(const PeriscopePtr& plugin, const FB::BrowserHostPtr& host) : m_plugin(plugin), m_host(host)
{
    registerMethod("echo",      make_method(this, &PeriscopeAPI::echo));
    registerMethod("testEvent", make_method(this, &PeriscopeAPI::testEvent));

    // Read-write property
    registerProperty("testString",
                     make_property(this,
                        &PeriscopeAPI::get_testString,
                        &PeriscopeAPI::set_testString));

    // Read-only property
    registerProperty("version",
                     make_property(this,
                        &PeriscopeAPI::get_version));

    // Register Periscope methods
    registerMethod("startBackgroundThread", make_method(this, &PeriscopeAPI::startBackgroundThread));
    registerMethod("stopBackgroundThread", make_method(this, &PeriscopeAPI::stopBackgroundThread));
    registerMethod("sendPage", make_method(this, &PeriscopeAPI::sendPage));
    registerMethod("getErrorMessage", make_method(this, &PeriscopeAPI::getErrorMessage));
    registerMethod("getBackgroundThreadStatus", make_method(this, &PeriscopeAPI::getBackgroundThreadStatus));
}

///////////////////////////////////////////////////////////////////////////////
/// @fn PeriscopeAPI::~PeriscopeAPI()
///
/// @brief  Destructor.  Remember that this object will not be released until
///         the browser is done with it; this will almost definitely be after
///         the plugin is released.
///////////////////////////////////////////////////////////////////////////////
PeriscopeAPI::~PeriscopeAPI()
{
}

///////////////////////////////////////////////////////////////////////////////
/// @fn PeriscopePtr PeriscopeAPI::getPlugin()
///
/// @brief  Gets a reference to the plugin that was passed in when the object
///         was created.  If the plugin has already been released then this
///         will throw a FB::script_error that will be translated into a
///         javascript exception in the page.
///////////////////////////////////////////////////////////////////////////////
PeriscopePtr PeriscopeAPI::getPlugin()
{
    PeriscopePtr plugin(m_plugin.lock());
    if (!plugin) {
        throw FB::script_error("The plugin is invalid");
    }
    return plugin;
}



// Read/Write property testString
std::string PeriscopeAPI::get_testString()
{
    return m_testString;
}
void PeriscopeAPI::set_testString(const std::string& val)
{
    m_testString = val;
}

// Read-only property version
std::string PeriscopeAPI::get_version()
{
    return "CURRENT_VERSION";
}

// Method echo
FB::variant PeriscopeAPI::echo(const FB::variant& msg)
{
    static int n(0);
    fire_echo(msg, n++);
    return msg;
}

void PeriscopeAPI::testEvent(const FB::variant& var)
{
    fire_fired(var, true, 1);
}

// Public Periscope methods

bool PeriscopeAPI::startBackgroundThread() {
    return PICL_startBackgroundThread() != 0;
}

bool PeriscopeAPI::stopBackgroundThread() {
    return PICL_stopBackgroundThread() != 0;
}

bool PeriscopeAPI::sendPage(const std::string& url, const std::string& pageContent) {
    return PICL_sendPage(url.c_str(), pageContent.c_str()) != 0;
}

std::string PeriscopeAPI::getErrorMessage() {
    char buffer[1024] = "";
    PICL_getErrorMessage(buffer, sizeof(buffer));
    return std::string(buffer);
}

std::string PeriscopeAPI::getBackgroundThreadStatus() {
    char buffer[1024] = "";
    PICL_getBackgroundThreadStatus(buffer, sizeof(buffer));
    return std::string(buffer);
}




