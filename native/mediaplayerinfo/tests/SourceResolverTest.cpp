#include "../src/SourceResolver.hpp"
#include <cassert>
#include <iostream>

using mediaplayerinfo::resolveSource;

int main() {
    {
        auto s = resolveSource(L"Spotify.exe", L"Track", L"Artist", L"", L"", L"");
        assert(s.sourceName == L"Spotify");
        assert(s.sourceType == L"service");
    }
    {
        auto s = resolveSource(L"chrome.exe", L"Track", L"Artist", L"", L"", L"https://music.youtube.com/watch?v=x");
        assert(s.appName == L"Google Chrome");
        assert(s.serviceName == L"YouTube Music");
        assert(s.sourceName == L"YouTube Music · Google Chrome");
    }
    {
        auto s = resolveSource(L"msedge.exe", L"Track", L"Artist", L"", L"", L"https://example.org/player");
        assert(s.serviceName == L"example.org");
        assert(s.sourceName == L"example.org · Microsoft Edge");
    }
    {
        auto s = resolveSource(L"firefox.exe", L"Track", L"Artist", L"", L"", L"");
        assert(s.sourceName == L"Mozilla Firefox");
        assert(s.sourceType == L"browser");
    }
    {
        auto s = resolveSource(L"C:\\Program Files\\VideoLAN\\VLC\\vlc.exe", L"", L"", L"", L"", L"");
        assert(s.sourceName == L"VLC");
        assert(s.sourceType == L"player");
    }
    std::wcout << L"SourceResolver tests passed\n";
}
