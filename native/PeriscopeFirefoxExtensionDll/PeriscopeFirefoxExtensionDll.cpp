// PeriscopeFirefoxExtensionDll.cpp : Defines the exported functions for the DLL application.
//

#include "stdafx.h"
#include "PeriscopeFirefoxExtensionDll.h"
#include "PeriscopeIndexerClient.h"

bool PFD_startBackgroundThread() {
    return PICL_startBackgroundThread();
}

bool PFD_stopBackgroundThread() {
    return PICL_stopBackgroundThread();
}

bool PFD_sendPage(char* url, char* pageContent) {
    return PICL_sendPage(url, pageContent);
}

void PFD_getErrorMessage(char* buffer, int bufLenChars) {
    std::string msg = PICL_getErrorMessage();
    strcpy_s(buffer, bufLenChars, msg.c_str());
}

void PFD_getBackgroundThreadStatus(char* buffer, int bufLenChars) {
    std::string msg = PICL_getBackgroundThreadStatus();
    strcpy_s(buffer, bufLenChars, msg.c_str());
}



