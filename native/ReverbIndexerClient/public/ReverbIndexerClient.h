#pragma once

#include <string>

// Exported function declarations
int RICL_startBackgroundThread();

int RICL_stopBackgroundThread();

int RICL_sendPage(const char* url, const char* pageContent);

void RICL_getErrorMessage(char* buf, int bufLen);

void RICL_getBackgroundThreadStatus(char* buf, int bufLen);

