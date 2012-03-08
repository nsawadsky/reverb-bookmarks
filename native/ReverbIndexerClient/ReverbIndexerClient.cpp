#include "stdafx.h"
#include "ReverbIndexerClient.h"

#include "XpNamedPipe.h"
#include "util.hpp"
using namespace util;

// Function typedefs
typedef BOOL (WINAPI *PWow64DisableWow64FsRedirection)(PVOID*);
typedef BOOL (WINAPI *PWow64RevertWow64FsRedirection)(PVOID);

// Local class declarations

enum MessageType {
    PageContent,
    ShutdownThread
};

class BackgroundThreadMessage {
public:
    BackgroundThreadMessage(MessageType type) {
        this->msgType = type;
    }

    virtual ~BackgroundThreadMessage() {} 

    MessageType getType() {
        return msgType;
    }

private:
    MessageType msgType;
};

class PageContentMessage : public BackgroundThreadMessage {
public:
    PageContentMessage(boost::shared_ptr<std::string> url, boost::shared_ptr<std::string> content) : BackgroundThreadMessage(PageContent) {
        this->url = url;
        this->content = content;
    }

    const std::string& getUrl() { return *url; }
    const std::string& getPageContent() { return *content; }

private:
    boost::shared_ptr<std::string> url;
    boost::shared_ptr<std::string> content;
};

class BackgroundThread {
public:
    BackgroundThread() : status("Thread not started") {
        threadStarted = false;

        this->pWow64DisableWow64FsRedirection = NULL;
        this->pWow64RevertWow64FsRedirection = NULL;

        HMODULE kernel32 = LoadLibrary(L"kernel32.dll");
        if (kernel32 != NULL) {
            this->pWow64DisableWow64FsRedirection = (PWow64DisableWow64FsRedirection)GetProcAddress(kernel32, "Wow64DisableWow64FsRedirection");
            this->pWow64RevertWow64FsRedirection = (PWow64RevertWow64FsRedirection)GetProcAddress(kernel32, "Wow64RevertWow64FsRedirection");
        }
    }
        
    void start() {
        boost::lock_guard<boost::mutex> guard(startupMutex);
        if (threadStarted) {
            return;
        }
        backgroundThread = boost::thread(boost::ref(*this));
        threadStarted = true;
    }

    void stop() {
        boost::lock_guard<boost::mutex> guard(startupMutex);
        if (!threadStarted) {
            return;
        }
        putMessage(boost::shared_ptr<BackgroundThreadMessage>(new BackgroundThreadMessage(ShutdownThread)));
        backgroundThread.join();
        threadStarted = false;
    }

    void putMessage(boost::shared_ptr<BackgroundThreadMessage> msg) {
        if (!threadStarted) {
            throw std::runtime_error("Background thread not started");
        }
        boost::lock_guard<boost::mutex> guard(queueMutex);
        bool wasEmpty = msgQueue.empty();
        msgQueue.push_back(msg);
        if (wasEmpty) {
            queueHasData.notify_one();
        }
    }

    std::string getStatus() {
        boost::lock_guard<boost::mutex> guard(statusMutex);
        return this->status;
    }

    void operator () () {
        XPNP_PipeHandle indexPipe = NULL;
        try {
            char pipeName[1024] = "";
            if (!XPNP_makePipeName("reverb-index", true, pipeName, sizeof(pipeName))) {
                throwXpnpError();
            }

            if (!SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_LOWEST)) {
                throwWindowsError("SetThreadPriority");
            }

            setStatus("Thread running");

            bool done = false;
            while (!done) {
                while (indexPipe == NULL) {
                    indexPipe = connectToIndexer(pipeName);
                    if (indexPipe == NULL) {
                        boost::this_thread::sleep(boost::posix_time::seconds(15));
                    }
                }

                boost::shared_ptr<BackgroundThreadMessage> msg = getNextMessage();
                switch (msg->getType()) {
                case PageContent: 
                    {
                        if (!handlePageContentMessage(boost::static_pointer_cast<PageContentMessage>(msg), indexPipe)) {
                            requeueMessage(msg);
                            XPNP_closePipe(indexPipe);
                            indexPipe = NULL;
                        }
                        break;
                    }
                case ShutdownThread: 
                    { 
                        setStatus("Thread received shutdown message");
                        done = true;
                        break;
                    }
                default: 
                    {
                        break;
                    }
                }
            }
        } catch (std::exception& except) {
            setStatus(except.what());
        }
        if (indexPipe != NULL) {
            XPNP_closePipe(indexPipe);
        }
    }

private:
    // Returns NULL if fails to connect for "typical" reason (e.g. indexer service not installed/running).
    // Throws exception if failure is "atypical", e.g. failed to get location of local appdata folder.
    XPNP_PipeHandle connectToIndexer(const char* pipeName) {
        XPNP_PipeHandle pipe = XPNP_openPipe(pipeName, true);
        if (pipe != NULL) {
            return pipe;
        }
        if (settingsPath.empty()) {
            wchar_t pathBuffer[MAX_PATH] = L"";
            HRESULT result = SHGetFolderPath(NULL, CSIDL_LOCAL_APPDATA, NULL, SHGFP_TYPE_CURRENT, pathBuffer);
            if (!SUCCEEDED(result)) {
                std::stringstream errorMsg;
                errorMsg << "SHGetFolderpath failed with " << HRESULT_CODE(result);
                throw std::runtime_error(errorMsg.str());
            }
            settingsPath = util::toUtf8(pathBuffer);
            settingsPath += "\\cs.ubc.ca\\reverb\\data\\settings";
        }
        std::ifstream inputStream(settingsPath + "\\indexer-install-path.txt");
        if (!inputStream) {
            setStatus("Indexer service not yet installed");
            return NULL;
        }

        std::string indexerInstallPath;
        std::getline(inputStream, indexerInstallPath);
        boost::trim_if(indexerInstallPath, boost::is_any_of(" \r\n"));
        inputStream.close();

        std::string indexerJarPath = indexerInstallPath + "\\ReverbIndexer.jar";
        if (!boost::filesystem::exists(indexerJarPath)) {
            return NULL;
        }

        STARTUPINFO startupInfo;
        memset(&startupInfo, 0, sizeof(startupInfo));
        startupInfo.cb = sizeof(startupInfo);

        PROCESS_INFORMATION processInfo;
        memset(&processInfo, 0, sizeof(processInfo));

        wchar_t commandLine[256] = L"javaw.exe -Djava.library.path=native -Xmx1024m -jar ReverbIndexer.jar";

        std::wstring indexerInstallPathUtf16 = util::toUtf16(indexerInstallPath);

        // On x64, prefer the x64 javaw.exe, if present.  Avoid making additional function calls while
        // redirection disabled, due to potential for unintended results.
        PVOID oldWowState = NULL;
        BOOL disableWowResult = FALSE;
        if (pWow64DisableWow64FsRedirection != NULL) {
            disableWowResult = pWow64DisableWow64FsRedirection(&oldWowState);
        }

        BOOL createResult = CreateProcess(NULL, commandLine, NULL, NULL, FALSE, 0, NULL, 
                indexerInstallPathUtf16.c_str(), &startupInfo, &processInfo);

        if (disableWowResult) {
            pWow64RevertWow64FsRedirection(&oldWowState);
        }

        if (!createResult) {
            if (!disableWowResult || !CreateProcess(NULL, commandLine, NULL, NULL, FALSE, 0, NULL, 
                    indexerInstallPathUtf16.c_str(), &startupInfo, &processInfo)) {
                setStatus(util::getWindowsErrorMessage("CreateProcess"));
                return NULL;
            }
        }

        CloseHandle(processInfo.hThread);
        CloseHandle(processInfo.hProcess);

        boost::this_thread::sleep(boost::posix_time::seconds(5));
        return XPNP_openPipe(pipeName, true);
    }

    bool handlePageContentMessage(boost::shared_ptr<PageContentMessage> msg, XPNP_PipeHandle pipe) {
        Json::Value root;
        Json::Value& pageInfo = root["message"]["updatePageInfoRequest"];
        pageInfo["url"] = msg->getUrl();
        pageInfo["html"] = msg->getPageContent();

        Json::FastWriter writer;
        std::string output = writer.write(root);

        int msgLength = output.length();
        msgLength = htonl(msgLength);
        int writeResult = XPNP_writePipe(pipe, (const char*)&msgLength, sizeof(msgLength));
        if (writeResult == 0) {
            return false;
        }
        writeResult = XPNP_writePipe(pipe, output.c_str(), output.length());
        return (writeResult != 0);
    }

    boost::shared_ptr<BackgroundThreadMessage> getNextMessage() {
        boost::unique_lock<boost::mutex> guard(queueMutex);
        while (msgQueue.empty()) {
            queueHasData.wait(guard);
        }
        boost::shared_ptr<BackgroundThreadMessage> result = msgQueue.front();
        msgQueue.pop_front();
        return result;
    }

    void requeueMessage(boost::shared_ptr<BackgroundThreadMessage> msg) {
        boost::lock_guard<boost::mutex> guard(queueMutex);
        msgQueue.push_front(msg);
    }

    void setStatus(const std::string& status) {
        boost::lock_guard<boost::mutex> guard(statusMutex);
        this->status = status;
    }

    void throwXpnpError() {
        char buffer[1024] = "";
        XPNP_getErrorMessage(buffer, sizeof(buffer));
        throw std::runtime_error(buffer);
    }

    boost::mutex queueMutex;
    boost::condition_variable queueHasData;
    std::list<boost::shared_ptr<BackgroundThreadMessage>> msgQueue;

    boost::mutex startupMutex;
    volatile bool threadStarted;
    boost::thread backgroundThread;
    
    boost::mutex statusMutex;
    std::string status;

    std::string settingsPath;

    PWow64DisableWow64FsRedirection pWow64DisableWow64FsRedirection;
    PWow64RevertWow64FsRedirection pWow64RevertWow64FsRedirection;
};

// Static instances
static BackgroundThread GBL_backgroundThread;

static boost::thread_specific_ptr<std::string> GBL_errorMessage;

// Local function definitions

static void setErrorMessage(const std::string& errorMessage) {
    GBL_errorMessage.reset(new std::string(errorMessage));
}

// Exported function definitions

int RICL_startBackgroundThread() {
    int success = 0;
    try {
        GBL_backgroundThread.start();
        success = 1;
    } catch (std::exception& except) {
        setErrorMessage(except.what());
    } 
    return success;
}

int RICL_stopBackgroundThread() {
    int success = 0;
    try {
        GBL_backgroundThread.stop();
        success = 1;
    } catch (std::exception& except) {
        setErrorMessage(except.what());
    } 
    return success;
}

int RICL_sendPage(const char* url, const char* pageContent) {
    int success = 0;
    try {
        boost::shared_ptr<std::string> urlString(new std::string(url));
        boost::shared_ptr<std::string> pageContentString(new std::string(pageContent));
        boost::shared_ptr<BackgroundThreadMessage> msg(new PageContentMessage(urlString, pageContentString));
        GBL_backgroundThread.putMessage(msg);
        success = 1;
    } catch (std::exception& except) {
        setErrorMessage(except.what());
    } 
    return success;
}

void RICL_getErrorMessage(char* buffer, int bufLen) {
    std::string* pErrorMsg = GBL_errorMessage.get();
    const char* errorMsg = "";
    if (pErrorMsg != NULL) {
        errorMsg = pErrorMsg->c_str();
    }
    if (strcpy_s(buffer, bufLen, errorMsg) != 0) {
        strcpy_s(buffer, bufLen, "Buffer too small");
    }
}

void RICL_getBackgroundThreadStatus(char* buffer, int bufLen) {
    std::string status = GBL_backgroundThread.getStatus();
    if (strcpy_s(buffer, bufLen, status.c_str()) != 0) {
        strcpy_s(buffer, bufLen, "Buffer too small");
    }
}



