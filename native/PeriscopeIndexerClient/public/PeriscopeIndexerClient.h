#pragma once

#include <string>

// Exported function declarations
int PICL_startBackgroundThread();

int PICL_stopBackgroundThread();

int PICL_sendPage(const char* url, const char* pageContent);

void PICL_getErrorMessage(char* buf, int bufLen);

void PICL_getBackgroundThreadStatus(char* buf, int bufLen);

