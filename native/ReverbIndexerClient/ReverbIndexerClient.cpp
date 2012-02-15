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
                boost::shared_ptr<BackgroundThreadMessage> msg = getNextMessage();
                switch (msg->getType()) {
                case PageContent: 
                    {
                        handlePageContentMessage(boost::static_pointer_cast<PageContentMessage>(msg), indexPipe);
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
    XPNP_PipeHandle connectToIndexer(const char* pipeName) {
        
        XPNP_PipeHandle result = XPNP_openPipe(pipeName, true);
        if (result == NULL) {
            if (indexerPath.empty()) {
                char* userProfilePath = getenv("USERPROFILE");
                
                wchar_t pathBuffer[MAX_PATH+1] = L"";
                HRESULT result = SHGetFolderPath(NULL, CSIDL_LOCAL_APPDATA, NULL, SHGFP_TYPE_CURRENT, pathBuffer);
                if (!SUCCEEDED(result)) {
                    std::stringstream errorMsg;
                    errorMsg << "SHGetFolderpath failed with " << HRESULT_CODE(result);
                    throw std::runtime_error(errorMsg.str());
                }
                indexerPath = util::toUtf8(pathBuffer);
                indexerPath += "\\cs.ubc.ca\\reverb\\code\\ReverbIndexer.jar";

            }
        } catch (...) { }
        return result;
    }

    void handlePageContentMessage(boost::shared_ptr<PageContentMessage> msg, XPNP_PipeHandle pipe) {
        Json::Value root;
        Json::Value& pageInfo = root["message"]["updatePageInfoRequest"];
        pageInfo["url"] = msg->getUrl();
        pageInfo["html"] = msg->getPageContent();

        Json::FastWriter writer;
        std::string output = writer.write(root);

        int msgLength = output.length();
        msgLength = htonl(msgLength);
        XPNP_writePipe(pipe, (const char*)&msgLength, sizeof(msgLength));
        XPNP_writePipe(pipe, output.c_str(), output.length());
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

    std::string indexerPath;
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



