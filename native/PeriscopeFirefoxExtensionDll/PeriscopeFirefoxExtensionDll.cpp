// PeriscopeFirefoxExtensionDll.cpp : Defines the exported functions for the DLL application.
//

#include "stdafx.h"
#include "PeriscopeFirefoxExtensionDll.h"
#include "PeriscopeIndexerClient.h"

bool PFED_startBackgroundThread() {
    return PICL_startBackgroundThread();
}

bool PFED_stopBackgroundThread() {
    return PICL_stopBackgroundThread();
}

bool PFED_sendPage(char* url, char* pageContent) {
    return PICL_sendPage(url, pageContent);
}

void PFED_getErrorMessage(char* buffer, int bufLenChars) {
    std::string msg = PICL_getErrorMessage();
    strcpy_s(buffer, bufLenChars, msg.c_str());
}

void PFED_getBackgroundThreadStatus(char* buffer, int bufLenChars) {
    std::string msg = PICL_getBackgroundThreadStatus();
    strcpy_s(buffer, bufLenChars, msg.c_str());
}



