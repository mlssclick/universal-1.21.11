#pragma once

#include <windows.h>

#include <string>
#include <string_view>
#include <vector>

#include "SourceResolver.hpp"

namespace mediaplayerinfo {

inline std::wstring browserProcessName(std::wstring_view appName) {
    if (appName == L"Google Chrome") return L"chrome.exe";
    if (appName == L"Microsoft Edge") return L"msedge.exe";
    if (appName == L"Mozilla Firefox") return L"firefox.exe";
    if (appName == L"Brave") return L"brave.exe";
    if (appName == L"Vivaldi") return L"vivaldi.exe";
    if (appName == L"Yandex Browser") return L"browser.exe";
    if (appName == L"Opera") return L"opera.exe";
    if (appName == L"Arc") return L"arc.exe";
    if (appName == L"Floorp") return L"floorp.exe";
    if (appName == L"Zen Browser") return L"zen.exe";
    return {};
}

inline std::wstring processImageName(DWORD processId) {
    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, processId);
    if (!process) return {};

    std::vector<wchar_t> buffer(32768);
    DWORD length = static_cast<DWORD>(buffer.size());
    std::wstring result;
    if (QueryFullProcessImageNameW(process, 0, buffer.data(), &length) && length > 0) {
        result.assign(buffer.data(), length);
        const auto slash = result.find_last_of(L"\\/");
        if (slash != std::wstring::npos && slash + 1 < result.size()) result.erase(0, slash + 1);
        result = lowerCopy(result);
    }
    CloseHandle(process);
    return result;
}

inline std::wstring windowTitle(HWND window) {
    const int length = GetWindowTextLengthW(window);
    if (length <= 0) return {};
    std::vector<wchar_t> buffer(static_cast<std::size_t>(length) + 1U, L'\0');
    const int written = GetWindowTextW(window, buffer.data(), static_cast<int>(buffer.size()));
    if (written <= 0) return {};
    return std::wstring(buffer.data(), static_cast<std::size_t>(written));
}

struct BrowserProbeContext {
    std::wstring expectedProcess;
    std::wstring mediaTitleLower;
    std::wstring service;
};

inline BOOL CALLBACK browserWindowCallback(HWND window, LPARAM parameter) {
    auto* context = reinterpret_cast<BrowserProbeContext*>(parameter);
    if (!context || !IsWindowVisible(window)) return TRUE;

    DWORD processId = 0;
    GetWindowThreadProcessId(window, &processId);
    if (processId == 0) return TRUE;

    const std::wstring process = processImageName(processId);
    if (process.empty() || process != context->expectedProcess) return TRUE;

    const std::wstring title = windowTitle(window);
    if (title.empty()) return TRUE;
    const std::wstring titleLower = lowerCopy(title);

    // Require the actual current media title to be present so another tab/window is not mistaken for the source.
    if (!context->mediaTitleLower.empty() && !contains(titleLower, context->mediaTitleLower)) return TRUE;

    context->service = serviceFromMetadata(titleLower);
    if (context->service.empty()) {
        context->service = extractHost(titleLower);
    }
    return context->service.empty() ? TRUE : FALSE;
}

inline std::wstring probeBrowserWindowService(std::wstring_view appName, std::wstring_view mediaTitle) {
    BrowserProbeContext context;
    context.expectedProcess = lowerCopy(browserProcessName(appName));
    context.mediaTitleLower = lowerCopy(mediaTitle);
    if (context.expectedProcess.empty() || context.mediaTitleLower.empty()) return {};
    EnumWindows(browserWindowCallback, reinterpret_cast<LPARAM>(&context));
    return context.service;
}

} // namespace mediaplayerinfo
