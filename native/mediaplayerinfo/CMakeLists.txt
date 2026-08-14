cmake_minimum_required(VERSION 3.24)
project(MediaPlayerInfo LANGUAGES CXX)

if(NOT WIN32)
    message(FATAL_ERROR "MediaPlayerInfo.dll must be built on Windows with the Windows SDK/C++/WinRT toolchain.")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(JNI REQUIRED)

add_library(MediaPlayerInfo SHARED
    src/MediaPlayerInfo.cpp
    src/SourceResolver.hpp
    src/BrowserWindowProbe.hpp
)

target_include_directories(MediaPlayerInfo PRIVATE ${JNI_INCLUDE_DIRS})
target_compile_definitions(MediaPlayerInfo PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE)

if(MSVC)
    target_compile_options(MediaPlayerInfo PRIVATE /EHsc /permissive- /utf-8 /W4)
endif()

target_link_libraries(MediaPlayerInfo PRIVATE windowsapp runtimeobject user32)

set_target_properties(MediaPlayerInfo PROPERTIES
    OUTPUT_NAME "MediaPlayerInfo"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
)
