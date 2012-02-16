#include "stdafx.h"
#include "ReverbIndexerClient.h"

#include "XpNamedPipe.h"
#include "util.hpp"
using namespace util;

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
        msgQueue.push(msg);
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
                if (indexPipe == NULL) {
                    indexPipe = connectToIndexer(pipeName);
                }
                if (indexPipe == NULL) {
                    boost::this_thread::sleep(boost::posix_time::seconds(15));
                } else {
                    boost::shared_ptr<BackgroundThreadMessage> msg = getNextMessage();
                    switch (msg->getType()) {
                    case PageContent: 
                        {
                            if (!handlePageContentMessage(boost::static_pointer_cast<PageContentMessage>(msg), indexPipe)) {
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
        if (codePath.empty()) {
            wchar_t pathBuffer[MAX_PATH] = L"";
            HRESULT result = SHGetFolderPath(NULL, CSIDL_LOCAL_APPDATA, NULL, SHGFP_TYPE_CURRENT, pathBuffer);
            if (!SUCCEEDED(result)) {
                std::stringstream errorMsg;
                errorMsg << "SHGetFolderpath failed with " << HRESULT_CODE(result);
                throw std::runtime_error(errorMsg.str());
            }
            codePath = util::toUtf8(pathBuffer);
            codePath += "\\cs.ubc.ca\\reverb\\code";
        }
        std::string indexerVersionPath = codePath + "\\indexer-version.txt";
        std::ifstream inputStream(indexerVersionPath);
        if (!inputStream) {
            setStatus("Indexer service not yet installed");
            return NULL;
        }

        int version = 0;
        inputStream >> version;
        if (!inputStream) {
            inputStream.close();
            setStatus("Indexer service not yet installed");
            return NULL;
        }
        inputStream.close();

        std::stringstream indexerJarPath;
        indexerJarPath << codePath << "\\" << version;

        STARTUPINFO startupInfo;
        memset(&startupInfo, 0, sizeof(startupInfo));
        startupInfo.cb = sizeof(startupInfo);

        PROCESS_INFORMATION processInfo;
        memset(&processInfo, 0, sizeof(processInfo));

        wchar_t commandLine[256] = L"javaw.exe -Djava.library.path=native -Xmx1024m -jar ReverbIndexer.jar";

        if (!CreateProcess(NULL, commandLine, NULL, NULL, FALSE, 0, NULL, 
                util::toUtf16(indexerJarPath.str()).c_str(), &startupInfo, &processInfo)) {
            setStatus(util::getWindowsErrorMessage("CreateProcess"));
            return NULL;
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
        msgQueue.pop();
        return result;
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
    std::queue<boost::shared_ptr<BackgroundThreadMessage>> msgQueue;

    boost::mutex startupMutex;
    volatile bool threadStarted;
    boost::thread backgroundThread;
    
    boost::mutex statusMutex;
    std::string status;

    std::string codePath;
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



