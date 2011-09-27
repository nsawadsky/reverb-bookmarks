/**********************************************************\

  Auto-generated PeriscopeAPI.cpp

\**********************************************************/

#include "JSObject.h"
#include "variant_list.h"
#include "DOM/Document.h"

#include "PeriscopeAPI.h"

// Periscope constants
const UINT MSG_PAGE_CONTENT = WM_USER;
const UINT MSG_SHUTDOWN_THREAD = MSG_PAGE_CONTENT + 1;

const wchar_t* PIPE_PREFIX = L"\\\\.\\pipe\\";
const int PIPE_BUF_SIZE = 10 * 1024;

const int BUF_LEN = 1024;

// Static Periscope fields
DWORD PeriscopeAPI::errorMessageTlsIndex = TlsAlloc();

CRITICAL_SECTION PeriscopeAPI::backgroundThreadStartupCS;
CRITICAL_SECTION PeriscopeAPI::backgroundThreadStatusCS;
std::wstring PeriscopeAPI::backgroundThreadStatus = L"Thread not started";

DWORD PeriscopeAPI::backgroundThreadId = 0;
HANDLE PeriscopeAPI::backgroundThread = NULL;
HANDLE PeriscopeAPI::backgroundThreadStarted = NULL;

volatile bool PeriscopeAPI::backgroundThreadExited = false;

bool PeriscopeAPI::classInitialized = PeriscopeAPI::initialize();

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

// Private Periscope methods
bool PeriscopeAPI::initialize() {
    InitializeCriticalSection(&backgroundThreadStartupCS);
    InitializeCriticalSection(&backgroundThreadStatusCS);
    return true;
}

bool PeriscopeAPI::setErrorMessage(const wchar_t* errorMessage) {
    wchar_t* currMessage = (wchar_t*)TlsGetValue(errorMessageTlsIndex);
    if (currMessage != NULL) {
        if (TlsSetValue(errorMessageTlsIndex, NULL)) {
            delete [] currMessage;
        } else {
            return false;
        }
    }
    if (errorMessage == NULL) {
        return true;
    } else {
        int bufSize = (int)wcslen(errorMessage) + 1;
        wchar_t* newMessage = new wchar_t[bufSize];
        if (newMessage == NULL) {
            return false;
        } else {
            wcscpy_s(newMessage, bufSize, errorMessage);
            if (TlsSetValue(errorMessageTlsIndex, newMessage)) {
                return true;
            } else {
                delete [] newMessage;
                return false;
            }
        }
    }
}

std::wstring PeriscopeAPI::getWindowsErrorMessage(wchar_t* funcName) {
    DWORD error = GetLastError();
    wchar_t buffer[BUF_LEN] = L"";
    FormatMessage(
        FORMAT_MESSAGE_FROM_SYSTEM |
        FORMAT_MESSAGE_IGNORE_INSERTS,
        NULL,
        error,
        MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
        buffer, BUF_LEN, NULL);
    wchar_t msg[BUF_LEN] = L"";
    swprintf_s(msg, L"%s failed with %lu: %s", funcName, error, buffer);
    return std::wstring(msg);
}

char* PeriscopeAPI::toUtf8(wchar_t* utf16) throw (std::wstring) {
    char* result = NULL;
    std::wstring errorMsg;
    bool error = false;
    try {
        int utf8BufLen = WideCharToMultiByte(CP_UTF8, 0, utf16, -1, NULL, 0, NULL, NULL);
        if (utf8BufLen == 0) {
            throw std::wstring(L"Error converting string to UTF-8: ") + getWindowsErrorMessage(L"WideCharToMultiByte");
        }
        result = new char[utf8BufLen];
        if (result == NULL) {
            throw std::wstring(L"Out of memory");
        }
        int conversionResult = WideCharToMultiByte(CP_UTF8, 0, utf16, -1, result, utf8BufLen, NULL, NULL);
        if (conversionResult == 0) {
            throw std::wstring(L"Error converting string to UTF-8: ") + getWindowsErrorMessage(L"WideCharToMultiByte");
        }
    } catch (std::wstring& tempErrorMsg) {
        errorMsg = tempErrorMsg;
        error = true;
        if (result != NULL) {
            delete [] result;
            result = NULL;
        }
    }
    if (error) {
        throw errorMsg;
    }
    return result;
}

void PeriscopeAPI::setBackgroundThreadStatus(const wchar_t* status) {
    EnterCriticalSection(&backgroundThreadStatusCS);
    backgroundThreadStatus = status;
    LeaveCriticalSection(&backgroundThreadStatusCS);
}

std::wstring PeriscopeAPI::getUserSid() throw (std::wstring) {
    bool error = false;
    std::wstring errorMsg;
    HANDLE tokenHandle = NULL;
    PTOKEN_USER pUserInfo = NULL;
    wchar_t* pSidString = NULL;
    std::wstring sid;
    try {
        if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &tokenHandle)) {
            throw getWindowsErrorMessage(L"OpenThreadToken");
        }
        DWORD userInfoSize = 0;
        GetTokenInformation(tokenHandle, TokenUser, NULL, 0, &userInfoSize);
        if (GetLastError() != ERROR_INSUFFICIENT_BUFFER) {
            throw getWindowsErrorMessage(L"GetTokenInformation");
        }
        pUserInfo = (PTOKEN_USER)malloc(userInfoSize);
        if (pUserInfo == NULL) {
            throw std::wstring(L"Out of memory");
        }
        if (!GetTokenInformation(tokenHandle, TokenUser, pUserInfo, userInfoSize, &userInfoSize)) {
            throw getWindowsErrorMessage(L"GetTokenInformation");
        }
        if (!ConvertSidToStringSid(pUserInfo->User.Sid, &pSidString)) {
            throw getWindowsErrorMessage(L"ConvertSidToStringSid");
        }
        sid = pSidString;
            
    } catch (std::wstring& tempErrorMsg) {
        error = true;
        errorMsg = tempErrorMsg;
    }
    if (tokenHandle != NULL) {
        CloseHandle(tokenHandle);
    }
    if (pUserInfo != NULL) {
        free(pUserInfo);
    }
    if (pSidString != NULL) {
        LocalFree(pSidString);
    }
    if (error) {
        throw errorMsg;
    }
    return sid;
}

std::wstring PeriscopeAPI::makePipeName(const wchar_t* shortName, bool userLocal) throw (std::wstring) {
    std::wstring pipeName;
    bool error = false;
    std::wstring errorMsg;
    try {
        wchar_t pipeNameBuffer[256] = L"";
        if (userLocal) {
            std::wstring userSid = getUserSid();

            HRESULT hr = swprintf_s(pipeNameBuffer, L"%s%s\\%s", PIPE_PREFIX, userSid.c_str(), shortName);
            if (FAILED(hr)) {
                throw std::wstring(L"When combined with user name and prefix, pipe name is too long");
            }
        } else {
            HRESULT hr = swprintf_s(pipeNameBuffer, L"%s%s", PIPE_PREFIX, shortName);
            if (FAILED(hr)) {
                throw std::wstring(L"When combined with prefix, pipe name is too long");
            }
        }
        pipeName = pipeNameBuffer;
    } catch (std::wstring tempErrorMsg) {
        error = true;
        errorMsg = tempErrorMsg;
    }
    if (error) {
        throw errorMsg;
    }
    return pipeName;
}

void PeriscopeAPI::handlePageContentMessage(MSG& msg, HANDLE pipe) throw (std::wstring) {
    bool error = false;
    std::wstring errorMsg;

    char* url = (char*)msg.wParam;
    char* pageData = (char*)msg.lParam;
    try {
        Json::Value root;
        Json::Value& pageInfo = root["message"]["pageInfo"];
        pageInfo["url"] = url;
        pageInfo["html"] = pageData;

        Json::FastWriter writer;
        std::string output = writer.write(root);

        DWORD bytesWritten = 0;
        BOOL result = WriteFile(pipe, output.c_str(), output.size(), &bytesWritten, NULL);
        if (!result) {
            throw std::wstring(L"Error writing to pipe: ") + getWindowsErrorMessage(L"WriteFile");
        }
    } catch (std::wstring& tempErrorMsg) {
        error = true;
        errorMsg = tempErrorMsg;
    }

    if (url != NULL) { delete [] url; }
    if (pageData != NULL) { delete [] pageData; }

    if (error) {
        throw errorMsg;
    }
}

DWORD WINAPI PeriscopeAPI::handleMessages(LPVOID param) {
    HANDLE indexPipe = INVALID_HANDLE_VALUE;
    try {
        // Create message queue
        MSG msg;
        PeekMessage(&msg, NULL, 0, 0, PM_NOREMOVE);

        if (!SetEvent(PeriscopeAPI::backgroundThreadStarted)) {
            throw std::wstring(L"Failed to set backgroundThreadStarted event") + PeriscopeAPI::getWindowsErrorMessage(L"SetEvent");
        }

        if (!SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_LOWEST)) {
            throw PeriscopeAPI::getWindowsErrorMessage(L"SetThreadPriority");
        }

        std::wstring pipeName = PeriscopeAPI::makePipeName(L"historyminer-index", true);

        int tries = 0;
        do {
            indexPipe = CreateFile(pipeName.c_str(), GENERIC_READ | GENERIC_WRITE, 0, NULL, OPEN_EXISTING, 0, NULL);
            tries++;
            if (indexPipe == INVALID_HANDLE_VALUE) {
                if (GetLastError() != ERROR_PIPE_BUSY) {
                    throw std::wstring(L"Failed to open pipe '") + pipeName + L"': " + PeriscopeAPI::getWindowsErrorMessage(L"CreateFile");
                }
                if (tries < 5) {
                    Sleep(100);
                }
            }
        } while (indexPipe == INVALID_HANDLE_VALUE && tries < 5);

        if (indexPipe == INVALID_HANDLE_VALUE) {
            throw std::wstring(L"Failed to open pipe '") + pipeName + L"': " + PeriscopeAPI::getWindowsErrorMessage(L"CreateFile");
        }

        DWORD mode = PIPE_READMODE_MESSAGE;
        if (! SetNamedPipeHandleState(indexPipe, &mode, NULL, NULL)) {
            throw std::wstring(L"Failed to set pipe to PIPE_READMODE_MESSAGE: ") + PeriscopeAPI::getWindowsErrorMessage(L"SetNamedPipeHandleState");
        }

        PeriscopeAPI::setBackgroundThreadStatus(L"Thread running");
        while (true) {
            if (!GetMessage(&msg, NULL, 0, 0)) {
                throw PeriscopeAPI::getWindowsErrorMessage(L"GetMessage");
            } else {
                switch (msg.message) {
                case MSG_PAGE_CONTENT: 
                    {
                        PeriscopeAPI::handlePageContentMessage(msg, indexPipe);
                        break;
                    }
                case MSG_SHUTDOWN_THREAD: 
                    {
                        throw std::wstring(L"Thread received shutdown message");
                    }
                default: 
                    {
                        break;
                    }
                }
            }
        }
    } catch (std::wstring& tempErrorMsg) {
        PeriscopeAPI::setBackgroundThreadStatus(tempErrorMsg.c_str());
    }

    if (indexPipe != INVALID_HANDLE_VALUE) {
        CloseHandle(indexPipe);
    }

    PeriscopeAPI::backgroundThreadExited = true;
    return 0;
}

// Public Periscope methods

bool PeriscopeAPI::startBackgroundThread() {
    bool success = false;
    EnterCriticalSection(&backgroundThreadStartupCS);
    try {
        if (backgroundThread != NULL) {
            success = true;
            throw 1;
        }
        backgroundThreadStarted = CreateEvent(NULL, TRUE, FALSE, NULL);
        if (backgroundThreadStarted == NULL) {
            throw getWindowsErrorMessage(L"CreateEvent");
        }
        backgroundThread = CreateThread(NULL, 0, handleMessages, NULL, 0, &backgroundThreadId);
        if (backgroundThread == NULL) {
            throw getWindowsErrorMessage(L"CreateThread");
        }
        DWORD waitResult = WaitForSingleObject(backgroundThreadStarted, 5000);
        if (waitResult != WAIT_OBJECT_0) {
            throw getWindowsErrorMessage(L"WaitForSingleObject");
        } 
        success = true;
    } catch (std::wstring& errorMsg) {
        setErrorMessage(errorMsg.c_str());
    } catch (...) {}
    LeaveCriticalSection(&backgroundThreadStartupCS);
    return success;
}

bool PeriscopeAPI::stopBackgroundThread() {
    if (backgroundThreadExited) {
        return true;
    }
    if (!PostThreadMessage(backgroundThreadId, MSG_SHUTDOWN_THREAD, NULL, NULL)) {
        setErrorMessage(getWindowsErrorMessage(L"PostThreadMessage").c_str());
        return false;
    }
    if (WaitForSingleObject(backgroundThread, INFINITE) != WAIT_OBJECT_0) {
        setErrorMessage(getWindowsErrorMessage(L"WaitForSingleObject").c_str());
        return false;
    }
    return true;
}

bool PeriscopeAPI::sendPage(const std::string& url, const std::string& pageContent) {
    bool success = false;
    char* urlBuffer = NULL;
    char* pageBuffer = NULL;
    try {
        if (backgroundThreadExited) {
            throw std::wstring(L"Background thread exited");
        }
        int urlLen = url.length() + 1;
        urlBuffer = new char[urlLen];
        if (urlBuffer == NULL) {
            throw std::wstring(L"Out of memory");
        }
        strcpy_s(urlBuffer, urlLen, url.c_str());

        int pageLen = pageContent.length() + 1;
        char* pageBuffer = new char[pageLen];
        if (pageBuffer == NULL) {
            throw std::wstring(L"Out of memory");
        }
        strcpy_s(pageBuffer, pageLen, pageContent.c_str());

        if (!PostThreadMessage(backgroundThreadId, MSG_PAGE_CONTENT, (WPARAM)urlBuffer, (LPARAM)pageBuffer)) {
           throw getWindowsErrorMessage(L"PostThreadMessage");
        }
        success = true;
    } catch (std::wstring& errorMsg) {
        setErrorMessage(errorMsg.c_str());
        if (urlBuffer != NULL) {
            delete [] urlBuffer;
        }
        if (pageBuffer != NULL) {
            delete [] pageBuffer;
        }
    }

    return success;
}

std::wstring PeriscopeAPI::getErrorMessage() {
    std::wstring errorMessageStr;
    wchar_t* errorMessage = (wchar_t*)TlsGetValue(errorMessageTlsIndex);
    if (errorMessage == NULL) {
        if (GetLastError() != ERROR_SUCCESS) {
            errorMessageStr = L"Failed to retrieve error message: ";
            errorMessageStr += getWindowsErrorMessage(L"TlsGetValue");
        }
    } else {
        errorMessageStr = errorMessage;
    }
    return errorMessageStr;
}

std::wstring PeriscopeAPI::getBackgroundThreadStatus() {
    std::wstring result;
    EnterCriticalSection(&backgroundThreadStatusCS);
    result = backgroundThreadStatus;
    LeaveCriticalSection(&backgroundThreadStatusCS);
    return result;
}




