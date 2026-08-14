#include <jni.h>
#include <windows.h>
#include <roapi.h>

#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Media.Control.h>
#include <winrt/Windows.Storage.Streams.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <limits>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "SourceResolver.hpp"
#include "BrowserWindowProbe.hpp"

namespace {
using winrt::Windows::Media::MediaPlaybackAutoRepeatMode;
using winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSession;
using winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionManager;
using winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionPlaybackStatus;
using winrt::Windows::Foundation::AsyncStatus;
using winrt::Windows::Storage::Streams::DataReader;

// No persistent SMTC manager/session or native worker is retained. cleanup() only
// requests cancellation of in-flight async operations, so the DLL itself cannot keep
// the Minecraft process alive.
std::atomic_bool gShuttingDown{false};

template <typename TAsync>
bool waitAsync(TAsync const& operation, DWORD timeoutMs) noexcept {
    const ULONGLONG startedAt = GetTickCount64();
    try {
        while (true) {
            const AsyncStatus status = operation.Status();
            if (status == AsyncStatus::Completed) return true;
            if (status == AsyncStatus::Canceled || status == AsyncStatus::Error) return false;
            if (gShuttingDown.load(std::memory_order_acquire)
                    || GetTickCount64() - startedAt >= timeoutMs) {
                try { operation.Cancel(); } catch (...) {}
                return false;
            }
            Sleep(2);
        }
    } catch (...) {
        return false;
    }
}

class RoApartment final {
public:
    RoApartment() noexcept : result_(RoInitialize(RO_INIT_MULTITHREADED)) {}
    ~RoApartment() {
        if (result_ == S_OK || result_ == S_FALSE) {
            RoUninitialize();
        }
    }

private:
    HRESULT result_;
};

GlobalSystemMediaTransportControlsSessionManager requestManager() {
    if (gShuttingDown.load(std::memory_order_acquire)) return nullptr;
    try {
        auto operation = GlobalSystemMediaTransportControlsSessionManager::RequestAsync();
        if (!waitAsync(operation, 1500)) return nullptr;
        return operation.GetResults();
    } catch (...) {
        return nullptr;
    }
}

winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionMediaProperties
readMediaProperties(GlobalSystemMediaTransportControlsSession const& session) {
    try {
        auto operation = session.TryGetMediaPropertiesAsync();
        if (!waitAsync(operation, 1500)) return nullptr;
        return operation.GetResults();
    } catch (...) {
        return nullptr;
    }
}

jstring toJavaString(JNIEnv* env, std::wstring_view value) {
    if (value.empty()) {
        return env->NewStringUTF("");
    }
    return env->NewString(reinterpret_cast<const jchar*>(value.data()), static_cast<jsize>(value.size()));
}

jstring toJavaString(JNIEnv* env, winrt::hstring const& value) {
    return toJavaString(env, std::wstring_view(value.c_str(), value.size()));
}

std::wstring fromJavaString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::wstring result(reinterpret_cast<const wchar_t*>(chars), static_cast<std::size_t>(length));
    env->ReleaseStringChars(value, chars);
    return result;
}

jobject newLinkedList(JNIEnv* env) {
    jclass listClass = env->FindClass("java/util/LinkedList");
    if (listClass == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(listClass, "<init>", "()V");
    if (constructor == nullptr) return nullptr;
    return env->NewObject(listClass, constructor);
}

bool listAdd(JNIEnv* env, jobject list, jobject value) {
    if (list == nullptr || value == nullptr) return false;
    jclass listClass = env->GetObjectClass(list);
    if (listClass == nullptr) return false;
    jmethodID add = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    if (add == nullptr) return false;
    return env->CallBooleanMethod(list, add, value) == JNI_TRUE;
}

std::vector<std::uint8_t> readArtwork(
        winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionMediaProperties const& properties) {
    std::vector<std::uint8_t> bytes;
    try {
        auto reference = properties.Thumbnail();
        if (!reference) return bytes;
        auto openOperation = reference.OpenReadAsync();
        if (!waitAsync(openOperation, 1200)) return bytes;
        auto stream = openOperation.GetResults();
        if (!stream) return bytes;
        const std::uint64_t streamSize = stream.Size();
        constexpr std::uint64_t maxArtworkBytes = 32ULL * 1024ULL * 1024ULL;
        if (streamSize == 0 || streamSize > maxArtworkBytes || streamSize > std::numeric_limits<std::uint32_t>::max()) {
            return bytes;
        }

        const auto size = static_cast<std::uint32_t>(streamSize);
        DataReader reader(stream.GetInputStreamAt(0));
        auto loadOperation = reader.LoadAsync(size);
        if (!waitAsync(loadOperation, 1200)) return bytes;
        const auto loaded = loadOperation.GetResults();
        if (loaded == 0) return bytes;
        bytes.resize(loaded);
        reader.ReadBytes(bytes);
    } catch (...) {
        bytes.clear();
    }
    return bytes;
}

jbyteArray toJavaBytes(JNIEnv* env, std::vector<std::uint8_t> const& bytes) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (result == nullptr || bytes.empty()) return result;
    env->SetByteArrayRegion(
            result,
            0,
            static_cast<jsize>(bytes.size()),
            reinterpret_cast<const jbyte*>(bytes.data()));
    return result;
}


long long toSeconds(winrt::Windows::Foundation::TimeSpan const& value) {
    return std::chrono::duration_cast<std::chrono::seconds>(value).count();
}

bool repeatSupported(GlobalSystemMediaTransportControlsSession const& session) {
    try {
        auto playback = session.GetPlaybackInfo();
        return playback && playback.Controls() && playback.Controls().IsRepeatEnabled();
    } catch (...) {
        return false;
    }
}

int repeatMode(GlobalSystemMediaTransportControlsSession const& session) {
    try {
        auto playback = session.GetPlaybackInfo();
        if (!playback || !playback.Controls() || !playback.Controls().IsRepeatEnabled()) return -1;
        auto mode = playback.AutoRepeatMode();
        if (!mode) return -1;
        return static_cast<int>(mode.Value());
    } catch (...) {
        return -1;
    }
}

void setStringField(JNIEnv* env, jobject object, const char* name, std::wstring_view value) {
    if (object == nullptr) return;
    jclass objectClass = env->GetObjectClass(object);
    if (objectClass == nullptr) return;
    jfieldID field = env->GetFieldID(objectClass, name, "Ljava/lang/String;");
    if (field == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    jstring stringValue = toJavaString(env, value);
    env->SetObjectField(object, field, stringValue);
    if (stringValue != nullptr) env->DeleteLocalRef(stringValue);
}

void setBooleanField(JNIEnv* env, jobject object, const char* name, bool value) {
    if (object == nullptr) return;
    jclass objectClass = env->GetObjectClass(object);
    if (objectClass == nullptr) return;
    jfieldID field = env->GetFieldID(objectClass, name, "Z");
    if (field == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    env->SetBooleanField(object, field, value ? JNI_TRUE : JNI_FALSE);
}

jobject createMediaInfo(
        JNIEnv* env,
        winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionMediaProperties const& properties,
        GlobalSystemMediaTransportControlsSession const& session) {
    jclass mediaInfoClass = env->FindClass("dev/redstones/mediaplayerinfo/MediaInfo");
    if (mediaInfoClass == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(
            mediaInfoClass,
            "<init>",
            "(Ljava/lang/String;Ljava/lang/String;[BJJZ)V");
    if (constructor == nullptr) return nullptr;

    auto timeline = session.GetTimelineProperties();
    auto playback = session.GetPlaybackInfo();

    const bool playing = playback
            && playback.PlaybackStatus() == GlobalSystemMediaTransportControlsSessionPlaybackStatus::Playing;

    const long long startSeconds = std::max(0LL, toSeconds(timeline.StartTime()));
    const long long endSeconds = std::max(0LL, toSeconds(timeline.EndTime()));

    // Match the original MediaPlayerInfo implementation: while media is playing,
    // timeline.Position() is only the last SMTC base position. Advance it by the
    // elapsed time since LastUpdatedTime() before returning it to Java. This keeps
    // every one-second poll current instead of periodically snapping several seconds
    // backwards when Windows refreshes its timeline.
    long long effectivePositionSeconds;
    if (playing) {
        effectivePositionSeconds = std::chrono::duration_cast<std::chrono::seconds>(
                winrt::clock::now() - timeline.LastUpdatedTime() + timeline.Position()).count();
    } else {
        effectivePositionSeconds = toSeconds(timeline.Position());
    }
    long long positionSeconds = std::max(0LL, effectivePositionSeconds - startSeconds);
    long long durationSeconds = std::max(0LL, endSeconds - startSeconds);

    // Some sessions expose a zero/invalid EndTime but a useful MaxSeekTime.
    if (durationSeconds <= 0) {
        const long long maxSeekSeconds = std::max(0LL, toSeconds(timeline.MaxSeekTime()));
        durationSeconds = std::max(0LL, maxSeekSeconds - startSeconds);
    }

    std::vector<std::uint8_t> artwork = readArtwork(properties);
    jstring title = toJavaString(env, properties.Title());
    jstring artist = toJavaString(env, properties.Artist());
    jbyteArray artworkArray = toJavaBytes(env, artwork);

    jobject mediaInfo = env->NewObject(
            mediaInfoClass,
            constructor,
            title,
            artist,
            artworkArray,
            static_cast<jlong>(positionSeconds),
            static_cast<jlong>(durationSeconds),
            playing ? JNI_TRUE : JNI_FALSE);

    if (title != nullptr) env->DeleteLocalRef(title);
    if (artist != nullptr) env->DeleteLocalRef(artist);
    if (artworkArray != nullptr) env->DeleteLocalRef(artworkArray);
    return mediaInfo;
}

jobject createWindowsSession(
        JNIEnv* env,
        jobject mediaInfo,
        GlobalSystemMediaTransportControlsSession const& session,
        std::uint32_t index,
        winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionMediaProperties const& properties) {
    jclass sessionClass = env->FindClass("dev/redstones/mediaplayerinfo/impl/win/WindowsMediaSession");
    if (sessionClass == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(
            sessionClass,
            "<init>",
            "(Ldev/redstones/mediaplayerinfo/MediaInfo;Ljava/lang/String;I)V");
    if (constructor == nullptr) return nullptr;

    winrt::hstring rawOwner = session.SourceAppUserModelId();
    jstring owner = toJavaString(env, rawOwner);
    jobject javaSession = env->NewObject(
            sessionClass,
            constructor,
            mediaInfo,
            owner,
            static_cast<jint>(index));
    if (owner != nullptr) env->DeleteLocalRef(owner);
    if (javaSession == nullptr) return nullptr;

    const winrt::hstring title = properties.Title();
    const winrt::hstring artist = properties.Artist();
    const winrt::hstring albumTitle = properties.AlbumTitle();
    const winrt::hstring albumArtist = properties.AlbumArtist();
    const winrt::hstring subtitle = properties.Subtitle();
    auto details = mediaplayerinfo::resolveSource(
            std::wstring_view(rawOwner.c_str(), rawOwner.size()),
            std::wstring_view(title.c_str(), title.size()),
            std::wstring_view(artist.c_str(), artist.size()),
            std::wstring_view(albumTitle.c_str(), albumTitle.size()),
            std::wstring_view(albumArtist.c_str(), albumArtist.size()),
            std::wstring_view(subtitle.c_str(), subtitle.size()));

    if (details.sourceType == L"browser" && details.serviceName.empty()) {
        const std::wstring probedService = mediaplayerinfo::probeBrowserWindowService(
                details.appName,
                std::wstring_view(title.c_str(), title.size()));
        if (!probedService.empty()) {
            details.serviceName = probedService;
            details.sourceName = probedService + L" · " + details.appName;
            details.sourceType = L"browser-site";
        }
    }

    setStringField(env, javaSession, "sourceAppId", std::wstring_view(rawOwner.c_str(), rawOwner.size()));
    setStringField(env, javaSession, "sourceAppName", details.appName);
    setStringField(env, javaSession, "sourceName", details.sourceName);
    setStringField(env, javaSession, "sourceType", details.sourceType);
    setStringField(env, javaSession, "serviceName", details.serviceName);
    setBooleanField(env, javaSession, "repeatSupported", repeatSupported(session));
    return javaSession;
}

std::wstring javaSessionSourceAppId(JNIEnv* env, jobject self) {
    if (self == nullptr) return {};
    jclass sessionClass = env->GetObjectClass(self);
    if (sessionClass == nullptr) return {};
    jfieldID field = env->GetFieldID(sessionClass, "sourceAppId", "Ljava/lang/String;");
    if (field == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        field = env->GetFieldID(sessionClass, "owner", "Ljava/lang/String;");
        if (field == nullptr) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return {};
        }
    }
    auto value = static_cast<jstring>(env->GetObjectField(self, field));
    std::wstring result = fromJavaString(env, value);
    if (value != nullptr) env->DeleteLocalRef(value);
    return result;
}

int javaSessionIndex(JNIEnv* env, jobject self) {
    if (self == nullptr) return -1;
    jclass sessionClass = env->GetObjectClass(self);
    if (sessionClass == nullptr) return -1;
    jfieldID field = env->GetFieldID(sessionClass, "index", "I");
    if (field == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return -1;
    }
    return env->GetIntField(self, field);
}

std::wstring javaSessionTitle(JNIEnv* env, jobject self) {
    if (self == nullptr) return {};
    jclass sessionClass = env->GetObjectClass(self);
    if (sessionClass == nullptr) return {};
    jfieldID mediaField = env->GetFieldID(sessionClass, "media", "Ldev/redstones/mediaplayerinfo/MediaInfo;");
    if (mediaField == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return {};
    }
    jobject media = env->GetObjectField(self, mediaField);
    if (media == nullptr) return {};
    jclass mediaClass = env->GetObjectClass(media);
    jmethodID titleMethod = mediaClass == nullptr ? nullptr : env->GetMethodID(mediaClass, "getTitle", "()Ljava/lang/String;");
    if (titleMethod == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(media);
        return {};
    }
    auto title = static_cast<jstring>(env->CallObjectMethod(media, titleMethod));
    std::wstring result = fromJavaString(env, title);
    if (title != nullptr) env->DeleteLocalRef(title);
    env->DeleteLocalRef(media);
    return result;
}

GlobalSystemMediaTransportControlsSession resolveSession(JNIEnv* env, jobject self) {
    auto mediaManager = requestManager();
    if (!mediaManager) return nullptr;
    auto sessions = mediaManager.GetSessions();
    const std::uint32_t size = sessions.Size();
    if (size == 0) return nullptr;

    const int requestedIndex = javaSessionIndex(env, self);
    const std::wstring appId = javaSessionSourceAppId(env, self);
    const std::wstring title = javaSessionTitle(env, self);

    if (requestedIndex >= 0 && static_cast<std::uint32_t>(requestedIndex) < size) {
        auto candidate = sessions.GetAt(static_cast<std::uint32_t>(requestedIndex));
        const winrt::hstring candidateRawId = candidate.SourceAppUserModelId();
        std::wstring candidateId(candidateRawId.c_str(), candidateRawId.size());
        if (appId.empty() || candidateId == appId) {
            return candidate;
        }
    }

    GlobalSystemMediaTransportControlsSession firstOwnerMatch{nullptr};
    for (std::uint32_t i = 0; i < size; ++i) {
        auto candidate = sessions.GetAt(i);
        const winrt::hstring candidateRawId = candidate.SourceAppUserModelId();
        std::wstring candidateId(candidateRawId.c_str(), candidateRawId.size());
        if (!appId.empty() && candidateId != appId) continue;
        if (!firstOwnerMatch) firstOwnerMatch = candidate;
        if (!title.empty()) {
            try {
                auto properties = readMediaProperties(candidate);
                if (!properties) continue;
                const winrt::hstring candidateRawTitle = properties.Title();
                std::wstring candidateTitle(candidateRawTitle.c_str(), candidateRawTitle.size());
                if (candidateTitle == title) return candidate;
            } catch (...) {
            }
        }
    }
    return firstOwnerMatch;
}

template <typename Action>
void executeAction(JNIEnv* env, jobject self, Action&& action) noexcept {
    if (gShuttingDown.load(std::memory_order_acquire)) return;
    RoApartment apartment;
    try {
        auto session = resolveSession(env, self);
        if (session) action(session);
    } catch (...) {
    }
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    gShuttingDown.store(false, std::memory_order_release);
    return JNI_VERSION_1_8;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*) {
    // No COM/WinRT work under JVM/native-library teardown. All WinRT objects are
    // call-local, so marking the bridge closed is sufficient and loader-lock safe.
    gShuttingDown.store(true, std::memory_order_release);
}

extern "C" JNIEXPORT jobject JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaPlayerInfo_getMediaSessions(
        JNIEnv* env,
        jobject /* self */) {
    jobject list = newLinkedList(env);
    if (list == nullptr || gShuttingDown.load(std::memory_order_acquire)) return list;
    RoApartment apartment;
    if (list == nullptr) return nullptr;

    try {
        auto mediaManager = requestManager();
        if (!mediaManager) return list;
        auto sessions = mediaManager.GetSessions();
        const std::uint32_t count = sessions.Size();
        for (std::uint32_t i = 0; i < count; ++i) {
            try {
                auto session = sessions.GetAt(i);
                auto properties = readMediaProperties(session);
                if (!properties) continue;
                jobject mediaInfo = createMediaInfo(env, properties, session);
                if (mediaInfo == nullptr) continue;
                jobject javaSession = createWindowsSession(env, mediaInfo, session, i, properties);
                if (javaSession != nullptr) {
                    listAdd(env, list, javaSession);
                    env->DeleteLocalRef(javaSession);
                }
                env->DeleteLocalRef(mediaInfo);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                }
            } catch (...) {
                if (env->ExceptionCheck()) env->ExceptionClear();
            }
        }
    } catch (...) {
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    return list;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaPlayerInfo_cleanup(
        JNIEnv* /* env */,
        jobject /* self */) {
    gShuttingDown.store(true, std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_play(
        JNIEnv* env,
        jobject self) {
    executeAction(env, self, [](auto const& session) { [[maybe_unused]] auto operation = session.TryPlayAsync(); });
}

extern "C" JNIEXPORT void JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_pause(
        JNIEnv* env,
        jobject self) {
    executeAction(env, self, [](auto const& session) { [[maybe_unused]] auto operation = session.TryPauseAsync(); });
}

extern "C" JNIEXPORT void JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_playPause(
        JNIEnv* env,
        jobject self) {
    executeAction(env, self, [](auto const& session) { [[maybe_unused]] auto operation = session.TryTogglePlayPauseAsync(); });
}

extern "C" JNIEXPORT void JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_stop(
        JNIEnv* env,
        jobject self) {
    executeAction(env, self, [](auto const& session) { [[maybe_unused]] auto operation = session.TryStopAsync(); });
}

extern "C" JNIEXPORT void JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_next(
        JNIEnv* env,
        jobject self) {
    executeAction(env, self, [](auto const& session) { [[maybe_unused]] auto operation = session.TrySkipNextAsync(); });
}

extern "C" JNIEXPORT void JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_previous(
        JNIEnv* env,
        jobject self) {
    executeAction(env, self, [](auto const& session) { [[maybe_unused]] auto operation = session.TrySkipPreviousAsync(); });
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_getCycleType(
        JNIEnv* env,
        jobject self) {
    if (gShuttingDown.load(std::memory_order_acquire)) return -1;
    RoApartment apartment;
    try {
        auto session = resolveSession(env, self);
        if (!session) return -1;
        return static_cast<jint>(repeatMode(session));
    } catch (...) {
        return -1;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_setCycleType(
        JNIEnv* env,
        jobject self,
        jint requestedMode) {
    if (requestedMode < 0 || requestedMode > 2 || gShuttingDown.load(std::memory_order_acquire)) {
        return JNI_FALSE;
    }

    RoApartment apartment;
    try {
        auto session = resolveSession(env, self);
        if (!session || !repeatSupported(session)) return JNI_FALSE;
        const auto mode = static_cast<MediaPlaybackAutoRepeatMode>(requestedMode);
        auto operation = session.TryChangeAutoRepeatModeAsync(mode);
        if (!waitAsync(operation, 900)) return JNI_FALSE;
        return operation.GetResults() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_redstones_mediaplayerinfo_impl_win_WindowsMediaSession_swapCycle(
        JNIEnv* env,
        jobject self) {
    executeAction(env, self, [](auto const& session) {
        auto playback = session.GetPlaybackInfo();
        if (!playback || !playback.Controls() || !playback.Controls().IsRepeatEnabled()) return;

        int current = 0;
        auto modeReference = playback.AutoRepeatMode();
        if (modeReference) {
            current = static_cast<int>(modeReference.Value());
        }

        // None -> Track -> List -> None. If a player rejects one mode, try the next one.
        for (int offset = 1; offset <= 3; ++offset) {
            const int candidate = (current + offset) % 3;
            const auto mode = static_cast<MediaPlaybackAutoRepeatMode>(candidate);
            try {
                auto operation = session.TryChangeAutoRepeatModeAsync(mode);
                if (waitAsync(operation, 900) && operation.GetResults()) {
                    return;
                }
            } catch (...) {
                // Some players advertise repeat but reject one of the individual modes.
            }
        }
    });
}
