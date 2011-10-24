#pragma once

#ifdef __cplusplus
extern "C" {
#endif

// Exported function declarations
__declspec(dllexport) int PFD_startBackgroundThread();

__declspec(dllexport) int PFD_stopBackgroundThread();

__declspec(dllexport) int PFD_sendPage(const char* url, char* pageContent);

__declspec(dllexport) void PFD_getErrorMessage(char* buffer, int bufLenChars);

__declspec(dllexport) void PFD_getBackgroundThreadStatus(char* buffer, int bufLenChars);

#ifdef __cplusplus
}
#endif
