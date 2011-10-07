/**********************************************************\

  Auto-generated PeriscopeAPI.h

\**********************************************************/

#include <string>
#include <sstream>
#include <boost/weak_ptr.hpp>
#include "JSAPIAuto.h"
#include "BrowserHost.h"
#include "Periscope.h"

#ifndef H_PeriscopeAPI
#define H_PeriscopeAPI

class PeriscopeAPI : public FB::JSAPIAuto
{
public:
    PeriscopeAPI(const PeriscopePtr& plugin, const FB::BrowserHostPtr& host);
    virtual ~PeriscopeAPI();

    PeriscopePtr getPlugin();

    // Read/Write property ${PROPERTY.ident}
    std::string get_testString();
    void set_testString(const std::string& val);

    // Read-only property ${PROPERTY.ident}
    std::string get_version();

    // Method echo
    FB::variant echo(const FB::variant& msg);
    
    // Event helpers
    FB_JSAPI_EVENT(fired, 3, (const FB::variant&, bool, int));
    FB_JSAPI_EVENT(echo, 2, (const FB::variant&, const int));
    FB_JSAPI_EVENT(notify, 0, ());

    // Method test-event
    void testEvent(const FB::variant& s);

    // Periscope methods
    bool startBackgroundThread();
    bool stopBackgroundThread();
    bool sendPage(const std::string& url, const std::string& pageContent);
    std::string getErrorMessage();
    std::string getBackgroundThreadStatus();

private:
    // Private fields
    PeriscopeWeakPtr m_plugin;
    FB::BrowserHostPtr m_host;

    std::string m_testString;
};

#endif // H_PeriscopeAPI

