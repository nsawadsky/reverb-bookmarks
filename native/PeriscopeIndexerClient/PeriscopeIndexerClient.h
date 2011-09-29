#pragma once

#include <string>

// Exported function declarations
bool PICL_startBackgroundThread();

bool PICL_stopBackgroundThread();

bool PICL_sendPage(const char* url, const char* pageContent);

std::string PICL_getErrorMessage();

std::string PICL_getBackgroundThreadStatus();

