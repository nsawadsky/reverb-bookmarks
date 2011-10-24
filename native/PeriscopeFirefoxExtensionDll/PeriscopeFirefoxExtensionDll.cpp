// PeriscopeFirefoxExtensionDll.cpp : Defines the exported functions for the DLL application.
//

#include "stdafx.h"
#include "PeriscopeFirefoxExtensionDll.h"
#include "PeriscopeIndexerClient.h"

int PFD_startBackgroundThread() {
    return PICL_startBackgroundThread();
}

int PFD_stopBackgroundThread() {
    return PICL_stopBackgroundThread();
}

int PFD_sendPage(char* url, char* pageContent) {
    return PICL_sendPage(url, pageContent);
}

void PFD_getErrorMessage(char* buffer, int bufLenChars) {
    PICL_getErrorMessage(buffer, bufLenChars);
}

void PFD_getBackgroundThreadStatus(char* buffer, int bufLenChars) {
    PICL_getBackgroundThreadStatus(buffer, bufLenChars);
}



