#pragma once

#ifdef __cplusplus
extern "C" {
#endif

// Exported function declarations
__declspec(dllexport) bool PFED_startBackgroundThread();

__declspec(dllexport) bool PFED_stopBackgroundThread();

__declspec(dllexport) bool PFED_sendPage(char* url, char* pageContent);

__declspec(dllexport) void PFED_getErrorMessage(char* buffer, int bufLenChars);

__declspec(dllexport) void PFED_getBackgroundThreadStatus(char* buffer, int bufLenChars);

#ifdef __cplusplus
}
#endif
