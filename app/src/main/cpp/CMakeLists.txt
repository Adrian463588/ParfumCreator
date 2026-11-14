cmake_minimum_required(VERSION 3.22.1)

project("parfume_core")

add_library(
    parfume_core
    SHARED
    parfume_core.cpp
)

target_include_directories(parfume_core PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(
    log-lib
    log
)

target_link_libraries(
    parfume_core
    ${log-lib}
)

target_compile_options(parfume_core PRIVATE
    -std=c++17
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -O3
    -fPIE
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
)

target_link_options(parfume_core PRIVATE
    -Wl,-z,relro,-z,now
    -Wl,--gc-sections
)
