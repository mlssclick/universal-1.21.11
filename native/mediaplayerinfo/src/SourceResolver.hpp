#pragma once

#include <algorithm>
#include <cwctype>
#include <string>
#include <string_view>
#include <vector>

namespace mediaplayerinfo {

struct SourceDetails {
    std::wstring appName;
    std::wstring serviceName;
    std::wstring sourceName;
    std::wstring sourceType;
};

inline std::wstring lowerCopy(std::wstring_view input) {
    std::wstring result(input.begin(), input.end());
    std::transform(result.begin(), result.end(), result.begin(), [](wchar_t ch) {
        return static_cast<wchar_t>(std::towlower(ch));
    });
    return result;
}

inline bool contains(std::wstring_view haystack, std::wstring_view needle) {
    return !needle.empty() && haystack.find(needle) != std::wstring_view::npos;
}

inline bool containsAny(std::wstring_view haystack, std::initializer_list<std::wstring_view> needles) {
    for (auto needle : needles) {
        if (contains(haystack, needle)) {
            return true;
        }
    }
    return false;
}

inline std::wstring trim(std::wstring value) {
    auto isSpace = [](wchar_t ch) { return std::iswspace(ch) != 0; };
    while (!value.empty() && isSpace(value.front())) value.erase(value.begin());
    while (!value.empty() && isSpace(value.back())) value.pop_back();
    return value;
}

inline std::wstring stripExecutable(std::wstring value) {
    value = trim(std::move(value));
    const auto slash = value.find_last_of(L"\\/");
    if (slash != std::wstring::npos && slash + 1 < value.size()) {
        value = value.substr(slash + 1);
    }
    auto lower = lowerCopy(value);
    if (lower.size() > 4 && lower.ends_with(L".exe")) {
        value.resize(value.size() - 4);
    }
    return value;
}

inline std::wstring browserFromOwner(std::wstring_view ownerLower) {
    if (containsAny(ownerLower, {L"msedge", L"microsoftedge"})) return L"Microsoft Edge";
    if (containsAny(ownerLower, {L"chrome", L"googlechrome"})) return L"Google Chrome";
    if (contains(ownerLower, L"firefox")) return L"Mozilla Firefox";
    if (contains(ownerLower, L"brave")) return L"Brave";
    if (contains(ownerLower, L"vivaldi")) return L"Vivaldi";
    if (containsAny(ownerLower, {L"yandexbrowser", L"yandex.browser", L"browser.exe"})) return L"Yandex Browser";
    if (contains(ownerLower, L"opera")) return L"Opera";
    if (contains(ownerLower, L"arc.exe") || contains(ownerLower, L"thebrowsercompany")) return L"Arc";
    if (contains(ownerLower, L"floorp")) return L"Floorp";
    if (contains(ownerLower, L"zen.exe") || contains(ownerLower, L"zen-browser")) return L"Zen Browser";
    return {};
}

inline std::wstring directServiceFromOwner(std::wstring_view ownerLower) {
    if (contains(ownerLower, L"spotify")) return L"Spotify";
    if (containsAny(ownerLower, {L"yandexmusic", L"yandex.music", L"music.yandex"})) return L"Yandex Music";
    if (contains(ownerLower, L"deezer")) return L"Deezer";
    if (contains(ownerLower, L"tidal")) return L"TIDAL";
    if (containsAny(ownerLower, {L"applemusic", L"apple.music", L"itunes"})) return L"Apple Music";
    if (contains(ownerLower, L"amazonmusic")) return L"Amazon Music";
    if (contains(ownerLower, L"soundcloud")) return L"SoundCloud";
    if (containsAny(ownerLower, {L"vkmusic", L"vk.music", L"boom.exe"})) return L"VK Music";
    if (contains(ownerLower, L"youtube")) return L"YouTube";
    return {};
}

inline std::wstring localPlayerFromOwner(std::wstring_view ownerLower) {
    if (contains(ownerLower, L"vlc")) return L"VLC";
    if (contains(ownerLower, L"foobar")) return L"foobar2000";
    if (contains(ownerLower, L"aimp")) return L"AIMP";
    if (contains(ownerLower, L"winamp")) return L"Winamp";
    if (contains(ownerLower, L"musicbee")) return L"MusicBee";
    if (containsAny(ownerLower, {L"zunemusic", L"microsoft.zunemusic", L"media player"})) return L"Windows Media Player";
    return {};
}

inline std::wstring serviceFromMetadata(std::wstring_view metadataLower) {
    if (containsAny(metadataLower, {L"music.youtube.com", L"youtube music"})) return L"YouTube Music";
    if (containsAny(metadataLower, {L"youtube.com", L"youtu.be", L"youtube"})) return L"YouTube";
    if (containsAny(metadataLower, {L"music.yandex.ru", L"music.yandex.com", L"яндекс музыка", L"yandex music"})) return L"Yandex Music";
    if (containsAny(metadataLower, {L"open.spotify.com", L"spotify"})) return L"Spotify";
    if (containsAny(metadataLower, {L"soundcloud.com", L"soundcloud"})) return L"SoundCloud";
    if (containsAny(metadataLower, {L"vk.com", L"vk music", L"вк музыка"})) return L"VK Music";
    if (containsAny(metadataLower, {L"music.apple.com", L"apple music"})) return L"Apple Music";
    if (containsAny(metadataLower, {L"deezer.com", L"deezer"})) return L"Deezer";
    if (containsAny(metadataLower, {L"tidal.com", L"tidal"})) return L"TIDAL";
    if (containsAny(metadataLower, {L"bandcamp.com", L"bandcamp"})) return L"Bandcamp";
    if (containsAny(metadataLower, {L"twitch.tv", L"twitch"})) return L"Twitch";
    if (containsAny(metadataLower, {L"mixcloud.com", L"mixcloud"})) return L"Mixcloud";
    if (containsAny(metadataLower, {L"music.amazon.", L"amazon music"})) return L"Amazon Music";
    return {};
}

inline std::wstring extractHost(std::wstring_view metadataLower) {
    std::size_t pos = metadataLower.find(L"https://");
    std::size_t prefix = 8;
    if (pos == std::wstring_view::npos) {
        pos = metadataLower.find(L"http://");
        prefix = 7;
    }
    if (pos == std::wstring_view::npos) {
        pos = metadataLower.find(L"www.");
        prefix = 0;
    }
    if (pos == std::wstring_view::npos) return {};

    std::size_t start = pos + prefix;
    std::size_t end = start;
    while (end < metadataLower.size()) {
        wchar_t ch = metadataLower[end];
        if (std::iswspace(ch) || ch == L'/' || ch == L'?' || ch == L'#' || ch == L'|' || ch == L')' || ch == L']') {
            break;
        }
        ++end;
    }
    if (end <= start) return {};
    std::wstring host(metadataLower.substr(start, end - start));
    while (!host.empty() && (host.back() == L'.' || host.back() == L',' || host.back() == L';')) host.pop_back();
    if (host.starts_with(L"www.")) host.erase(0, 4);
    return host;
}

inline SourceDetails resolveSource(
        std::wstring_view rawOwner,
        std::wstring_view title,
        std::wstring_view artist,
        std::wstring_view albumTitle,
        std::wstring_view albumArtist,
        std::wstring_view subtitle) {
    const std::wstring ownerLower = lowerCopy(rawOwner);
    const std::wstring browser = browserFromOwner(ownerLower);
    const std::wstring directService = directServiceFromOwner(ownerLower);
    const std::wstring localPlayer = localPlayerFromOwner(ownerLower);

    std::wstring metadata;
    metadata.reserve(title.size() + artist.size() + albumTitle.size() + albumArtist.size() + subtitle.size() + 16);
    metadata.append(title).append(L" | ").append(artist).append(L" | ")
            .append(albumTitle).append(L" | ").append(albumArtist).append(L" | ").append(subtitle);
    const std::wstring metadataLower = lowerCopy(metadata);

    SourceDetails details;
    if (!directService.empty()) {
        details.appName = directService;
        details.serviceName = directService;
        details.sourceName = directService;
        details.sourceType = L"service";
        return details;
    }

    if (!browser.empty()) {
        details.appName = browser;
        details.serviceName = serviceFromMetadata(metadataLower);
        if (details.serviceName.empty()) {
            const std::wstring host = extractHost(metadataLower);
            if (!host.empty()) details.serviceName = host;
        }
        if (!details.serviceName.empty()) {
            details.sourceName = details.serviceName + L" · " + browser;
            details.sourceType = L"browser-site";
        } else {
            details.sourceName = browser;
            details.sourceType = L"browser";
        }
        return details;
    }

    if (!localPlayer.empty()) {
        details.appName = localPlayer;
        details.sourceName = localPlayer;
        details.sourceType = L"player";
        return details;
    }

    details.appName = stripExecutable(std::wstring(rawOwner));
    if (details.appName.empty()) details.appName = L"Unknown media source";
    details.sourceName = details.appName;
    details.sourceType = L"app";
    return details;
}

} // namespace mediaplayerinfo
