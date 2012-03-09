#pragma once

#ifdef __cplusplus
extern "C" {
#endif

// Exported function declarations
__declspec(dllexport) int RFD_startBackgroundThread();

__declspec(dllexport) int RFD_stopBackgroundThread();

__declspec(dllexport) int RFD_sendPage(const char* url, char* pageContent);

__declspec(dllexport) void RFD_getErrorMessage(char* buffer, int bufLenChars);

__declspec(dllexport) void RFD_getBackgroundThreadStatus(char* buffer, int bufLenChars);

#ifdef __cplusplus
}
#endif
