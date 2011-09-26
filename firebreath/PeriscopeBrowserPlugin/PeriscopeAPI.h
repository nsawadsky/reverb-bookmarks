/**********************************************************\

  Auto-generated PeriscopeAPI.h

\**********************************************************/

#include <winsdkver.h>
#define _WIN32_WINNT _WIN32_WINNT_WS03
#include <SDKDDKVer.h>

#define WIN32_LEAN_AND_MEAN             // Exclude rarely-used stuff from Windows headers
// Windows Header Files:
#include <windows.h>
#include <sddl.h>
#include <Rpc.h>
#include <WinSock2.h>

#include <json.h>

// Avoid warnings for use of throw as exception specification.
#pragma warning( disable : 4290 )

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
    bool sendPage(const std::wstring& url, const std::wstring& pageContent);
    std::wstring getErrorMessage();
    std::wstring getBackgroundThreadStatus();

private:
    // Private Periscope methods
    bool setErrorMessage(const wchar_t* errorMessage);
    std::wstring getWindowsErrorMessage(wchar_t* funcName);
    void setBackgroundThreadStatus(const wchar_t* status);

    char* toUtf8(wchar_t* utf16) throw (std::wstring);

    std::wstring getUserSid() throw (std::wstring);

    std::wstring makePipeName(const wchar_t* shortName, bool userLocal) throw (std::wstring);

    void handlePageContentMessage(MSG& msg, HANDLE pipe);

    static DWORD WINAPI handleMessages(LPVOID param);

    // Private fields
    PeriscopeWeakPtr m_plugin;
    FB::BrowserHostPtr m_host;

    std::string m_testString;

    // Private Periscope fields
    DWORD errorMessageTlsIndex;

    CRITICAL_SECTION backgroundThreadStartupCS;
    CRITICAL_SECTION backgroundThreadStatusCS;
    std::wstring backgroundThreadStatus;

    DWORD backgroundThreadId;
    HANDLE backgroundThread;
    HANDLE backgroundThreadStarted;

    volatile bool backgroundThreadExited;
};

#endif // H_PeriscopeAPI

