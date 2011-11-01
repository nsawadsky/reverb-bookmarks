// ReverbFirefoxExtensionDll.cpp : Defines the exported functions for the DLL application.
//

#include "stdafx.h"
#include "ReverbFirefoxExtensionDll.h"
#include "ReverbIndexerClient.h"

int RFD_startBackgroundThread() {
    return RICL_startBackgroundThread();
}

int RFD_stopBackgroundThread() {
    return RICL_stopBackgroundThread();
}

int RFD_sendPage(const char* url, char* pageContent) {
    return RICL_sendPage(url, pageContent);
}

void RFD_getErrorMessage(char* buffer, int bufLenChars) {
    RICL_getErrorMessage(buffer, bufLenChars);
}

void RFD_getBackgroundThreadStatus(char* buffer, int bufLenChars) {
    RICL_getBackgroundThreadStatus(buffer, bufLenChars);
}



