/**********************************************************\

  Auto-generated ReverbAPI.cpp

\**********************************************************/

#include "JSObject.h"
#include "variant_list.h"
#include "DOM/Document.h"

#include "ReverbAPI.h"
#include "ReverbIndexerClient.h"

///////////////////////////////////////////////////////////////////////////////
/// @fn ReverbAPI::ReverbAPI(const ReverbPtr& plugin, const FB::BrowserHostPtr host)
///
/// @brief  Constructor for your JSAPI object.  You should register your methods, properties, and events
///         that should be accessible to Javascript from here.
///
/// @see FB::JSAPIAuto::registerMethod
/// @see FB::JSAPIAuto::registerProperty
/// @see FB::JSAPIAuto::registerEvent
///////////////////////////////////////////////////////////////////////////////
ReverbAPI::ReverbAPI(const ReverbPtr& plugin, const FB::BrowserHostPtr& host) : m_plugin(plugin), m_host(host)
{
    registerMethod("echo",      make_method(this, &ReverbAPI::echo));
    registerMethod("testEvent", make_method(this, &ReverbAPI::testEvent));

    // Read-write property
    registerProperty("testString",
                     make_property(this,
                        &ReverbAPI::get_testString,
                        &ReverbAPI::set_testString));

    // Read-only property
    registerProperty("version",
                     make_property(this,
                        &ReverbAPI::get_version));

    // Register Reverb methods
    registerMethod("startBackgroundThread", make_method(this, &ReverbAPI::startBackgroundThread));
    registerMethod("stopBackgroundThread", make_method(this, &ReverbAPI::stopBackgroundThread));
    registerMethod("sendPage", make_method(this, &ReverbAPI::sendPage));
    registerMethod("getErrorMessage", make_method(this, &ReverbAPI::getErrorMessage));
    registerMethod("getBackgroundThreadStatus", make_method(this, &ReverbAPI::getBackgroundThreadStatus));
}

///////////////////////////////////////////////////////////////////////////////
/// @fn ReverbAPI::~ReverbAPI()
///
/// @brief  Destructor.  Remember that this object will not be released until
///         the browser is done with it; this will almost definitely be after
///         the plugin is released.
///////////////////////////////////////////////////////////////////////////////
ReverbAPI::~ReverbAPI()
{
}

///////////////////////////////////////////////////////////////////////////////
/// @fn ReverbPtr ReverbAPI::getPlugin()
///
/// @brief  Gets a reference to the plugin that was passed in when the object
///         was created.  If the plugin has already been released then this
///         will throw a FB::script_error that will be translated into a
///         javascript exception in the page.
///////////////////////////////////////////////////////////////////////////////
ReverbPtr ReverbAPI::getPlugin()
{
    ReverbPtr plugin(m_plugin.lock());
    if (!plugin) {
        throw FB::script_error("The plugin is invalid");
    }
    return plugin;
}



// Read/Write property testString
std::string ReverbAPI::get_testString()
{
    return m_testString;
}
void ReverbAPI::set_testString(const std::string& val)
{
    m_testString = val;
}

// Read-only property version
std::string ReverbAPI::get_version()
{
    return "CURRENT_VERSION";
}

// Method echo
FB::variant ReverbAPI::echo(const FB::variant& msg)
{
    static int n(0);
    fire_echo(msg, n++);
    return msg;
}

void ReverbAPI::testEvent(const FB::variant& var)
{
    fire_fired(var, true, 1);
}

// Public Reverb methods

bool ReverbAPI::startBackgroundThread() {
    return RICL_startBackgroundThread() != 0;
}

bool ReverbAPI::stopBackgroundThread() {
    return RICL_stopBackgroundThread() != 0;
}

bool ReverbAPI::sendPage(const std::string& url, const std::string& pageContent) {
    return RICL_sendPage(url.c_str(), pageContent.c_str()) != 0;
}

std::string ReverbAPI::getErrorMessage() {
    char buffer[1024] = "";
    RICL_getErrorMessage(buffer, sizeof(buffer));
    return std::string(buffer);
}

std::string ReverbAPI::getBackgroundThreadStatus() {
    char buffer[1024] = "";
    RICL_getBackgroundThreadStatus(buffer, sizeof(buffer));
    return std::string(buffer);
}

