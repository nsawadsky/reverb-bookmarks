#include "stdafx.h"
#include "PeriscopeIndexerClient.h"

#include "XpNamedPipe.h"
#include "util.hpp"
using namespace util;

const UINT MSG_PAGE_CONTENT = WM_USER;
const UINT MSG_SHUTDOWN_THREAD = MSG_PAGE_CONTENT + 1;

static boost::thread_specific_ptr<std::string> GBL_errorMessage;

static CRITICAL_SECTION GBL_backgroundThreadStartupCS;
static CRITICAL_SECTION GBL_backgroundThreadStatusCS;
static std::string GBL_backgroundThreadStatus = "Thread not started";

static DWORD GBL_backgroundThreadId = 0;
static HANDLE GBL_backgroundThread = NULL;
static HANDLE GBL_backgroundThreadStarted = NULL;

static volatile bool GBL_backgroundThreadExited = false;

static bool initializeGlobals() {
    InitializeCriticalSection(&GBL_backgroundThreadStartupCS);
    InitializeCriticalSection(&GBL_backgroundThreadStatusCS);
    return true;
}

static bool GBL_globalsInitialized = initializeGlobals();

// Local function definitions

static void setErrorMessage(const std::string& errorMessage) {
    GBL_errorMessage.reset(new std::string(errorMessage));
}

static void throwXpnpError() {
    char buffer[1024] = "";
    XPNP_getErrorMessage(buffer, sizeof(buffer));
    throw std::runtime_error(buffer);
}

static void setBackgroundThreadStatus(const std::string& status) {
    EnterCriticalSection(&GBL_backgroundThreadStatusCS);
    GBL_backgroundThreadStatus = status;
    LeaveCriticalSection(&GBL_backgroundThreadStatusCS);
}

static void writeMessage(XPNP_PipeHandle pipe, const char* msg, int msgLen) {
    int msgLenNetwork = htonl(msgLen);
    XPNP_writePipe(pipe, (const char*)&msgLenNetwork, sizeof(msgLenNetwork));
    XPNP_writePipe(pipe, msg, msgLen);
}

static void handlePageContentMessage(MSG& msg, XPNP_PipeHandle pipe) {
    boost::scoped_ptr<std::string> url((std::string*)msg.wParam);
    boost::scoped_ptr<std::string> pageData((std::string*)msg.lParam);

    Json::Value root;
    Json::Value& pageInfo = root["message"]["pageInfo"];
    pageInfo["url"] = *url;
    pageInfo["html"] = *pageData;

    Json::FastWriter writer;
    std::string output = writer.write(root);

    writeMessage(pipe, output.c_str(), output.length());
}

static DWORD WINAPI handleMessages(LPVOID param) {
    XPNP_PipeHandle indexPipe = NULL;
    try {
        // Create message queue
        MSG msg;
        PeekMessage(&msg, NULL, 0, 0, PM_NOREMOVE);

        if (!SetEvent(GBL_backgroundThreadStarted)) {
            throwWindowsError("Failed to set GBL_backgroundThreadStarted event", "SetEvent");
        }

        if (!SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_LOWEST)) {
            throwWindowsError("SetThreadPriority");
        }

        indexPipe = XPNP_openPipe("historyminer-index", true);
        if (indexPipe == NULL) {
            throwXpnpError();
        }
            
        setBackgroundThreadStatus("Thread running");
        bool done = false;
        while (!done) {
            if (!GetMessage(&msg, NULL, 0, 0)) {
                throwWindowsError("GetMessage");
            } else {
                switch (msg.message) {
                case MSG_PAGE_CONTENT: 
                    {
                        handlePageContentMessage(msg, indexPipe);
                        break;
                    }
                case MSG_SHUTDOWN_THREAD: 
                    {
                        setBackgroundThreadStatus("Thread received shutdown message");
                        done = true;
                        break;
                    }
                default: 
                    {
                        break;
                    }
                }
            }
        }
    } catch (std::exception& except) {
        setBackgroundThreadStatus(except.what());
    }

    if (indexPipe != NULL) {
        XPNP_closePipe(indexPipe);
    }

    GBL_backgroundThreadExited = true;
    return 0;
}

// Exported function definitions

int PICL_startBackgroundThread() {
    int success = 0;
    EnterCriticalSection(&GBL_backgroundThreadStartupCS);
    try {
        if (GBL_backgroundThread != NULL) {
            success = 1;
        } else {
            GBL_backgroundThreadStarted = CreateEvent(NULL, TRUE, FALSE, NULL);
            if (GBL_backgroundThreadStarted == NULL) {
                throwWindowsError("CreateEvent");
            }
            GBL_backgroundThread = CreateThread(NULL, 0, handleMessages, NULL, 0, &GBL_backgroundThreadId);
            if (GBL_backgroundThread == NULL) {
                throwWindowsError("CreateThread");
            }
            DWORD waitResult = WaitForSingleObject(GBL_backgroundThreadStarted, 5000);
            if (waitResult == WAIT_TIMEOUT) {
                throw std::runtime_error("Timed out waiting for background thread to start");
            } else if (waitResult == WAIT_FAILED) {
                throwWindowsError("WaitForSingleObject");
            } 
            success = 1;
        }
    } catch (std::exception& except) {
        setErrorMessage(except.what());
    } 
    LeaveCriticalSection(&GBL_backgroundThreadStartupCS);
    return success;
}

int PICL_stopBackgroundThread() {
    if (GBL_backgroundThreadExited) {
        return 1;
    }
    if (!PostThreadMessage(GBL_backgroundThreadId, MSG_SHUTDOWN_THREAD, NULL, NULL)) {
        setErrorMessage(getWindowsErrorMessage("PostThreadMessage"));
        return 0;
    }
    if (WaitForSingleObject(GBL_backgroundThread, INFINITE) != WAIT_OBJECT_0) {
        setErrorMessage(getWindowsErrorMessage("WaitForSingleObject"));
        return 0;
    }
    return 1;
}

int PICL_sendPage(const char* url, const char* pageContent) {
    int success = 0;
    std::string* urlString = NULL;
    std::string* pageContentString = NULL;
    try {
        if (GBL_backgroundThreadExited) {
            throw std::runtime_error("Background thread exited");
        }
        urlString = new std::string(url);
        pageContentString = new std::string(pageContent);

        if (!PostThreadMessage(GBL_backgroundThreadId, MSG_PAGE_CONTENT, (WPARAM)urlString, (LPARAM)pageContentString)) {
            throwWindowsError("PostThreadMessage");
        }
        success = 1;
    } catch (std::exception& except) {
        setErrorMessage(except.what());
        if (urlString != NULL) {
            delete urlString;
        }
        if (pageContentString != NULL) {
            delete pageContentString;
        }
    }

    return success;
}

void PICL_getErrorMessage(char* buffer, int bufLen) {
    std::string* pErrorMsg = GBL_errorMessage.get();
    const char* errorMsg = "";
    if (pErrorMsg != NULL) {
        errorMsg = pErrorMsg->c_str();
    }
    if (strcpy_s(buffer, bufLen, errorMsg) != 0) {
        strcpy_s(buffer, bufLen, "Buffer too small");
    }
}

void PICL_getBackgroundThreadStatus(char* buffer, int bufLen) {
    EnterCriticalSection(&GBL_backgroundThreadStatusCS);
    if (strcpy_s(buffer, bufLen, GBL_backgroundThreadStatus.c_str()) != 0) {
        strcpy_s(buffer, bufLen, "Buffer too small");
    }
    LeaveCriticalSection(&GBL_backgroundThreadStatusCS);
}



