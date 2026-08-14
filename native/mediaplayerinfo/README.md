# MediaPlayerInfo native DLL

Windows x64 JNI bridge for Global System Media Transport Controls (SMTC).

## What it exposes

- media sessions
- title / artist / artwork
- position / duration / playing state
- raw SourceAppUserModelId
- best-effort application/browser/service/site identification
- play / pause / toggle / previous / next / stop
- repeat: Off / Track / List, cycling and direct mode selection
- clean native shutdown

## Lifecycle

The DLL intentionally keeps **no process-global SMTC manager/session cache**. Every JNI call obtains a short-lived manager/session reference and releases it when that call returns. `cleanup()` marks the bridge as shutting down, so later JNI calls become no-ops/empty results. `JNI_OnUnload` only marks the bridge closed and performs no COM/WinRT work during native-library teardown.

This means the DLL itself creates no worker thread and holds no long-lived WinRT object that can keep Minecraft alive. The Java integration stops its daemon poller/action executor before calling `cleanup()`.

Website detection is best-effort. SMTC exposes the source application's AppUserModelId, not a browser tab URL. The DLL checks media metadata and then, for browsers, a matching browser window title. If there is not enough reliable information, it reports the browser rather than inventing a site.

## Build

Requirements:

- Windows 10/11 x64
- Visual Studio with **Desktop development with C++**
- Windows 10/11 SDK with C++/WinRT headers
- CMake
- JDK with `JAVA_HOME` set

From PowerShell:

```powershell
cd native\mediaplayerinfo
.\build.ps1 Release
```

The DLL is always written to:

`native/mediaplayerinfo/dist/MediaPlayerInfo.dll`

When the script is run inside the full mod project, it also installs the DLL to:

`src/main/resources/mediaplayerinfo/natives/win/MediaPlayerInfo.dll`
