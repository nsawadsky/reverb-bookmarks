#include "stdafx.h"
#include "PeriscopeIndexerClient.h"

const UINT MSG_PAGE_CONTENT = WM_USER;
const UINT MSG_SHUTDOWN_THREAD = MSG_PAGE_CONTENT + 1;

const wchar_t* PIPE_PREFIX = L"\\\\.\\pipe\\";
const int PIPE_BUF_SIZE = 10 * 1024;

const int BUF_LEN = 1024;
static DWORD GBL_errorMessageTlsIndex = TlsAlloc();

static CRITICAL_SECTION GBL_backgroundThreadStartupCS;
static CRITICAL_SECTION GBL_backgroundThreadStatusCS;
static wchar_t GBL_backgroundThreadStatus[BUF_LEN] = L"Thread not started";

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

// Local function declarations
static bool setErrorMessage(const wchar_t* errorMessage);
static std::wstring getWindowsErrorMessage(wchar_t* funcName);
static void setBackgroundThreadStatus(const wchar_t* status);

static char* toUtf8(wchar_t* utf16) throw (std::wstring);

static std::wstring getUserSid() throw (std::wstring);

static std::wstring makePipeName(const wchar_t* shortName, bool userLocal) throw (std::wstring);

static void handlePageContentMessage(MSG& msg, HANDLE pipe);

static DWORD WINAPI handleMessages(LPVOID param);

// Local function definitions

bool setErrorMessage(const wchar_t* errorMessage) {
    wchar_t* currMessage = (wchar_t*)TlsGetValue(GBL_errorMessageTlsIndex);
    if (currMessage != NULL) {
        if (TlsSetValue(GBL_errorMessageTlsIndex, NULL)) {
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
            if (TlsSetValue(GBL_errorMessageTlsIndex, newMessage)) {
                return true;
            } else {
                delete [] newMessage;
                return false;
            }
        }
    }
}

std::wstring getWindowsErrorMessage(wchar_t* funcName) {
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

char* toUtf8(wchar_t* utf16) throw (std::wstring) {
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

void setBackgroundThreadStatus(const wchar_t* status) {
    EnterCriticalSection(&GBL_backgroundThreadStatusCS);
    wcscpy_s(GBL_backgroundThreadStatus, status);
    LeaveCriticalSection(&GBL_backgroundThreadStatusCS);
}

std::wstring getUserSid() throw (std::wstring) {
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

std::wstring makePipeName(const wchar_t* shortName, bool userLocal) throw (std::wstring) {
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

void handlePageContentMessage(MSG& msg, HANDLE pipe) {
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

DWORD WINAPI handleMessages(LPVOID param) {
    HANDLE indexPipe = INVALID_HANDLE_VALUE;
    try {
        // Create message queue
        MSG msg;
        PeekMessage(&msg, NULL, 0, 0, PM_NOREMOVE);

        if (!SetEvent(GBL_backgroundThreadStarted)) {
            throw std::wstring(L"Failed to set GBL_backgroundThreadStarted event") + getWindowsErrorMessage(L"SetEvent");
        }

        if (!SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_LOWEST)) {
            throw getWindowsErrorMessage(L"SetThreadPriority");
        }

        std::wstring pipeName = makePipeName(L"historyminer-index", true);

        int tries = 0;
        do {
            indexPipe = CreateFile(pipeName.c_str(), GENERIC_READ | GENERIC_WRITE, 0, NULL, OPEN_EXISTING, 0, NULL);
            tries++;
            if (indexPipe == INVALID_HANDLE_VALUE) {
                if (GetLastError() != ERROR_PIPE_BUSY) {
                    throw std::wstring(L"Failed to open pipe '") + pipeName + L"': " + getWindowsErrorMessage(L"CreateFile");
                }
                if (tries < 5) {
                    Sleep(100);
                }
            }
        } while (indexPipe == INVALID_HANDLE_VALUE && tries < 5);

        if (indexPipe == INVALID_HANDLE_VALUE) {
            throw std::wstring(L"Failed to open pipe '") + pipeName + L"': " + getWindowsErrorMessage(L"CreateFile");
        }

        DWORD mode = PIPE_READMODE_MESSAGE;
        if (! SetNamedPipeHandleState(indexPipe, &mode, NULL, NULL)) {
            throw std::wstring(L"Failed to set pipe to PIPE_READMODE_MESSAGE: ") + getWindowsErrorMessage(L"SetNamedPipeHandleState");
        }

        setBackgroundThreadStatus(L"Thread running");
        while (true) {
            if (!GetMessage(&msg, NULL, 0, 0)) {
                throw getWindowsErrorMessage(L"GetMessage");
            } else {
                switch (msg.message) {
                case MSG_PAGE_CONTENT: 
                    {
                        handlePageContentMessage(msg, indexPipe);
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
        setBackgroundThreadStatus(tempErrorMsg.c_str());
    }

    if (indexPipe != INVALID_HANDLE_VALUE) {
        CloseHandle(indexPipe);
    }

    GBL_backgroundThreadExited = true;
    return 0;
}

// Exported function definitions

bool PICL_startBackgroundThread() {
    bool success = false;
    EnterCriticalSection(&GBL_backgroundThreadStartupCS);
    try {
        if (GBL_backgroundThread != NULL) {
            success = true;
            throw 1;
        }
        GBL_backgroundThreadStarted = CreateEvent(NULL, TRUE, FALSE, NULL);
        if (GBL_backgroundThreadStarted == NULL) {
            throw getWindowsErrorMessage(L"CreateEvent");
        }
        GBL_backgroundThread = CreateThread(NULL, 0, handleMessages, NULL, 0, &GBL_backgroundThreadId);
        if (GBL_backgroundThread == NULL) {
            throw getWindowsErrorMessage(L"CreateThread");
        }
        DWORD waitResult = WaitForSingleObject(GBL_backgroundThreadStarted, 5000);
        if (waitResult != WAIT_OBJECT_0) {
            throw getWindowsErrorMessage(L"WaitForSingleObject");
        } 
        success = true;
    } catch (std::wstring& errorMsg) {
        setErrorMessage(errorMsg.c_str());
    } catch (...) {}
    LeaveCriticalSection(&GBL_backgroundThreadStartupCS);
    return success;
}

bool PICL_stopBackgroundThread() {
    if (GBL_backgroundThreadExited) {
        return true;
    }
    if (!PostThreadMessage(GBL_backgroundThreadId, MSG_SHUTDOWN_THREAD, NULL, NULL)) {
        setErrorMessage(getWindowsErrorMessage(L"PostThreadMessage").c_str());
        return false;
    }
    if (WaitForSingleObject(GBL_backgroundThread, INFINITE) != WAIT_OBJECT_0) {
        setErrorMessage(getWindowsErrorMessage(L"WaitForSingleObject").c_str());
        return false;
    }
    return true;
}

bool PICL_sendPage(char* url, char* pageContent) {
    bool success = false;
    char* urlBuffer = NULL;
    char* pageBuffer = NULL;
    try {
        if (GBL_backgroundThreadExited) {
            throw std::wstring(L"Background thread exited");
        }
        int urlLen = strlen(url) + 1;
        urlBuffer = new char[urlLen];
        if (urlBuffer == NULL) {
            throw std::wstring(L"Out of memory");
        }
        strcpy_s(urlBuffer, urlLen, url);

        int pageLen = strlen(pageContent) + 1;
        char* pageBuffer = new char[pageLen];
        if (pageBuffer == NULL) {
            throw std::wstring(L"Out of memory");
        }
        strcpy_s(pageBuffer, pageLen, pageContent);

        if (!PostThreadMessage(GBL_backgroundThreadId, MSG_PAGE_CONTENT, (WPARAM)urlBuffer, (LPARAM)pageBuffer)) {
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

void PICL_getErrorMessage(wchar_t* buffer, int bufLenChars) {
    std::wstring errorMessageStr;
    wchar_t* errorMessage = (wchar_t*)TlsGetValue(GBL_errorMessageTlsIndex);
    if (errorMessage == NULL) {
        if (GetLastError() != ERROR_SUCCESS) {
            errorMessageStr = L"Failed to retrieve error message: ";
            errorMessageStr += getWindowsErrorMessage(L"TlsGetValue");
        }
    } else {
        errorMessageStr = errorMessage;
    }
    wcscpy_s(buffer, bufLenChars, errorMessageStr.c_str());
}

void PICL_getBackgroundThreadStatus(wchar_t* buffer, int bufLenChars) {
    EnterCriticalSection(&GBL_backgroundThreadStatusCS);
    wcscpy_s(buffer, bufLenChars, GBL_backgroundThreadStatus);
    LeaveCriticalSection(&GBL_backgroundThreadStatusCS);
}



