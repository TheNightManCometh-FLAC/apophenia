cmake_minimum_required(VERSION 3.22.1)
project(apophenia_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(apophenia_audio SHARED
    jni_bridge.cpp
    lab_engine.cpp
    engine.cpp
)

target_link_libraries(apophenia_audio
    oboe::oboe
    log
)

# Signalsmith Stretch 1.4.0 + Signalsmith Linear 0.6.4 (MIT, header-only, vendored unmodified)
target_include_directories(apophenia_audio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/signalsmith-stretch/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/signalsmith-linear/include
)
