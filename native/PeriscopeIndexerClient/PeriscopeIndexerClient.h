#pragma once

#ifdef __cplusplus
extern "C" {
#endif

// Exported function declarations
bool PICL_startBackgroundThread();

bool PICL_stopBackgroundThread();

bool PICL_sendPage(char* url, char* pageContent);

void PICL_getErrorMessage(wchar_t* buffer, int bufLenChars);

void PICL_getBackgroundThreadStatus(wchar_t* buffer, int bufLenChars);

#ifdef __cplusplus
}
#endif
